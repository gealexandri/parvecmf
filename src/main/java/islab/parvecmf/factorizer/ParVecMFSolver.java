package islab.parvecmf.factorizer;

import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.SparseRowMatrix;
import org.apache.mahout.math.Vector;

/**
 *
 * Solver invoked by
 * @see islab.parvecmf.factorizer.ParVecMFFactorizer
 *
 */
class ParVecMFSolver {
    static Vector solve(Vector ratingVector, Vector neuralEmbeddingVector, Matrix featureMatrix, Matrix inverseMatrix,
                        double lambda, double c) {

        Matrix A = new SparseRowMatrix(1, ratingVector.size());
        A.assignRow(0, ratingVector);

        A = A.times(featureMatrix).times(c);
        Vector aVector = A.viewRow(0);
        A.assignRow(0, aVector.plus(neuralEmbeddingVector.times(lambda)));

        return A.times(inverseMatrix).viewRow(0);
    }
}
