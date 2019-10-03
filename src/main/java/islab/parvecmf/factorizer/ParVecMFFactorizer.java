package islab.parvecmf.factorizer;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.IDMigrator;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Implements the ParVecMF methodology, as described in "<a href="https://arxiv.org/abs/1706.07513">ParVecMF: A
 * Paragraph Vector-based Matrix Factorization Recommender System</a>" and in
 * "<a href="https://doi.org/10.1145/3308560.3316601">From Free-text User Reviews to Product Recommendation using
 * Paragraph Vectors and Matrix Factorization</a>".
 *
 * <br><br>See a working example <a href="https://git.islab.ntua.gr/gealexandri/parvecmf-example">here</a>.
 */
public class ParVecMFFactorizer extends AbstractFactorizer {
    private final DataModel dataModel;
    private final IDMigrator idMigrator;
    private final int numFeatures, numIterations, numTrainingThreads;
    private final String strUserModel, strItemModel;
    private final double lu, lv, c;

    public double Usum, Vsum;

    private static final Logger log = LoggerFactory.getLogger(ParVecMFFactorizer.class);

    /**
     * Create the factorizer
     *
     * @param model A {@link DataModel} containing user-to-item ratings
     * @param idm An {@link IDMigrator} that maps alphanumerical user and item ids to numerical values
     * @param numFeatures Total number of features
     * @param strUserModel Location of the file containing the user paragraph vectors (see README.md and the
     *                     <a href="https://git.islab.ntua.gr/gealexandri/parvecmf-example">example code</a> for
     *                     details)
     * @param strItemModel Location of the file containing the item paragraph vectors (see README.md and the
     *                     <a href="https://git.islab.ntua.gr/gealexandri/parvecmf-example">example code</a> for
     *                     details)
     * @param numIterations Number of iterations
     * @param lu λ<sub>u</sub> hyperparameter
     * @param lv λ<sub>v</sub> hyperparameter
     * @param c c hyperparameter
     * @param numTrainingThreads Number of training threads
     * @throws TasteException In case of error
     */
    public ParVecMFFactorizer(DataModel model, IDMigrator idm, int numFeatures, String strUserModel, String strItemModel,
                              int numIterations, double lu, double lv, double c, int numTrainingThreads)
            throws TasteException {
        super(model);
        this.dataModel = Preconditions.checkNotNull(model);
        this.idMigrator = Preconditions.checkNotNull(idm);
        this.strUserModel = Preconditions.checkNotNull(strUserModel);
        this.strItemModel = Preconditions.checkNotNull(strItemModel);
        this.numFeatures = numFeatures;
        this.numIterations = numIterations;
        this.numTrainingThreads = numTrainingThreads;
        this.lu = lu;
        this.lv = lv;
        this.c = c;
    }

    static class Features {
        private final DataModel dataModel;
        private final IDMigrator idMigrator;
        private final int numFeatures;

        private final double[][] U, V, I, J;

        Features(ParVecMFFactorizer factorizer) throws TasteException, IOException {
            dataModel = factorizer.dataModel;
            numFeatures = factorizer.numFeatures;
            idMigrator = factorizer.idMigrator;
            Random random = RandomUtils.getRandom();

            // Load user paragraph vectors
            I = new double[dataModel.getNumUsers()][numFeatures];

            Stream<String> iStream = Files.lines(Paths.get(factorizer.strUserModel));

            iStream.skip(1).forEach(line -> {
                String[] part = line.split(" ", numFeatures + 1);

                int idx = factorizer.userIndex(idMigrator.toLongID(part[0]));

                for (int i=1; i < part.length; i++)
                    I[idx][i-1] = Double.parseDouble(part[i]);

            });

            iStream.close();

            // Load item paragraph vectors
            J = new double[dataModel.getNumItems()][numFeatures];

            Stream<String> jStream = Files.lines(Paths.get(factorizer.strItemModel));

            jStream.skip(1).forEach(line -> {
                String[] part = line.split(" ", numFeatures + 1);

                int idx = factorizer.itemIndex(idMigrator.toLongID(part[0]));

                for (int i=1; i < part.length; i++)
                    I[idx][i-1] = Double.parseDouble(part[i]);

            });

            iStream.close();

            // Initialize the user-feature matrix
            U = new double[dataModel.getNumUsers()][numFeatures];

            // Initialize the item-feature matrix to small random values
            V = new double[dataModel.getNumItems()][numFeatures];
            LongPrimitiveIterator itemIDsIterator = dataModel.getItemIDs();
            while (itemIDsIterator.hasNext()) {
                long itemID = itemIDsIterator.nextLong();
                int itemIDIndex = factorizer.itemIndex(itemID);
                V[itemIDIndex][0] = averateRating(dataModel.getPreferencesForItem(itemID));
                for (int feature = 0; feature < numFeatures; feature++) {
                    V[itemIDIndex][feature] = random.nextDouble() * 0.1;
                }
            }
        }

        double[][] getU() {
            return U;
        }

        double[][] getV() {
            return V;
        }

        Vector getUserFeatureRow(int index) {
            return new DenseVector(U[index]);
        }

        Vector getItemFeatureRow(int index) {
            return new DenseVector(V[index]);
        }

        Vector getUserNeuralEmbeddings(int index) {
            return new DenseVector(I[index]);
        }

        Vector getItemNeuralEmbeddings(int index) {
            return new DenseVector(J[index]);
        }

        void setFeatureRowInU(int idIndex, Vector vector) {
            setFeatureColumn(U, idIndex, vector);
        }

        void setFeatureRowInV(int idIndex, Vector vector) {
            setFeatureColumn(V, idIndex, vector);
        }

        protected void setFeatureColumn(double[][] matrix, int idIndex, Vector vector) {
            for (int feature = 0; feature < numFeatures; feature++) {
                matrix[idIndex][feature] = vector.get(feature);
            }
        }

        protected double averateRating(PreferenceArray prefs) throws TasteException {
            RunningAverage avg = new FullRunningAverage();
            for (Preference pref : prefs) {
                avg.addDatum(pref.getValue());
            }
            return avg.getAverage();
        }
    }

    @Override
    public Factorization factorize() throws TasteException {
        log.info("starting to compute the factorization...");

        int numUsers = dataModel.getNumUsers();
        int numItems = dataModel.getNumItems();

        try {
            final Features features = new Features(this);

            for (int iteration = 1; iteration <= numIterations; iteration++) {
                log.info("iteration {}", iteration);

                /* fix V - compute U */
                Matrix V = new DenseMatrix(features.getV());
                Matrix invV =  new DenseMatrix(getInverseMatrix(features.getV(), c, lu));

                ExecutorService queue = createQueue();
                LongPrimitiveIterator userIDsIterator = dataModel.getUserIDs();
                try {
                    while (userIDsIterator.hasNext()) {
                        final long userID = userIDsIterator.nextLong();
                        final PreferenceArray userPrefs = dataModel.getPreferencesFromUser(userID);

                        queue.execute(() -> {
                            Vector userFeatures = ParVecMFSolver.solve(
                                    ratingVector(userPrefs, numItems, false, this),
                                    features.getUserNeuralEmbeddings(userIndex(userID)),
                                    V,
                                    invV,
                                    lu,
                                    c
                            );

                            features.setFeatureRowInU(userIndex(userID), userFeatures);
                        });
                    }
                } finally {
                    queue.shutdown();
                    try {
                        queue.awaitTermination(dataModel.getNumUsers(), TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        log.warn("Error when computing user features", e);
                    }
                }

                /* fix U - compute Vt */
                Matrix U = new DenseMatrix(features.getU());
                Matrix invU =  new DenseMatrix(getInverseMatrix(features.getU(), c, lv));

                queue = createQueue();
                LongPrimitiveIterator itemIDsIterator = dataModel.getItemIDs();
                try {
                    while (itemIDsIterator.hasNext()) {
                        final long itemID = itemIDsIterator.nextLong();
                        final PreferenceArray itemPrefs = dataModel.getPreferencesForItem(itemID);
                        queue.execute(() -> {
                            Vector itemFeatures = ParVecMFSolver.solve(
                                    ratingVector(itemPrefs, numUsers, true, this),
                                    features.getItemNeuralEmbeddings(itemIndex(itemID)),
                                    U,
                                    invU,
                                    lv,
                                    c
                            );

                            features.setFeatureRowInV(itemIndex(itemID), itemFeatures);
                        });
                    }
                } finally {
                    queue.shutdown();
                    try {
                        queue.awaitTermination(dataModel.getNumItems(), TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        log.warn("Error when computing item features", e);
                    }
                }
            }

            Usum = (new DenseMatrix(features.getU())).zSum();
            Vsum = (new DenseMatrix(features.getV())).zSum();

            log.info("finished computing the factorization...");
            return createFactorization(features.getU(), features.getV());
        } catch (IOException e) {
            throw new TasteException("IOException: " + e.getMessage());
        }
    }

    private double[][] getInverseMatrix(double[][] features, double c, double lambda) {
        RealMatrix F = new Array2DRowRealMatrix(features);
        RealMatrix Ft = F.transpose();

        RealMatrix FFt = Ft.multiply(F).scalarMultiply(c);

        double[] data = new double[F.getColumnDimension()];
        java.util.Arrays.fill(data, lambda);
        RealMatrix lI = new org.apache.commons.math3.linear.DiagonalMatrix(data);

        return MatrixUtils.inverse(FFt.add(lI)).getData();

    }

    protected ExecutorService createQueue() {
        return Executors.newFixedThreadPool(numTrainingThreads);
    }

    protected static Vector ratingVector(PreferenceArray prefs, int size, boolean userPrefs, ParVecMFFactorizer ft) {
        Vector rating = new SequentialAccessSparseVector(size);

        for (Preference pref : prefs)
            rating.setQuick((userPrefs) ? ft.userIndex(pref.getUserID()) : ft.itemIndex(pref.getItemID()),
                    pref.getValue());

        return rating;
    }
}