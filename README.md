# ParVecMF

Repository containing the implementation of the ParVecMF methodology introduced in "[ParVecMF: A Paragraph Vector-based Matrix Factorization Recommender System](https://arxiv.org/abs/1706.07513)" and in "[From Free-text User Reviews to Product Recommendation using Paragraph Vectors and Matrix Factorization](https://doi.org/10.1145/3308560.3316601)".

## Requirements

1. Oracle Java Version 1.8
2. Apache Maven 3.6.0

## Usage

1. Clone the repository to a local folderL (e.g. ```git clone https://github.com/gealexandri/parvecmf.git```)
2. Change to that folder and issue ```mvn package```
3. Install the generated jar files to your local maven repository, issuing ```mvn install:install-file -Dfile=./target/parvecmf-0.0.1.jar -DpomFile=./pom.xml -Dsources=./target/parvecmf-0.0.1-sources.jar -Djavadoc=./target/parvecmf-0.0.1-javadoc.jar```

Class documentation is also available at the ```./javadoc/``` directory of the currrent repository.

For a working example, please clone the [parvecmf-example](https://github.com/gealexandri/parvecmf-example) repository.


## Paragraph Vectors for Users & Items

The paragraph vectors of the user/item reviews are introduced as external text files to the [ParVecMFFactorizer](https://htmlpreview.github.io/?https://github.com/gealexandri/parvecmf/blob/master/javadoc/islab/parvecmf/factorizer/ParVecMFFactorizer.html) class. Their structure should be as follows

<pre>
N F
ID1 v11 v12 v13 ... v1F
ID2 v21 v22 v23 ... v2F
...
IDN vN1 vN2 vN3 ... vNF
</pre>

where:
1. **N**: the total number of user/item ids in the file, respectively
2. **F**: is the number of features
3. **ID1** - **IDN**: the **N** distinct user/item ids
4. **v11** - **vNF**: the feature values

Ids and feature values are separated by a single white space. The snippet below demonstrates example paragraph vectors of two users with 5 features each.

<pre>
2 5
userA 2.548369 1.093621 0.571134 5.497886 11.709472
userB 4.662368 7.707105 14.424696 3.574620 1.097487
</pre>
 

## Licence & Citations

The source code is provided under an Apache Licence, Version 2 (please read LICENCE.txt for more details). If you plan to use this code in your project or research, please cite the following two publications:

<pre>
@inproceedings{Alexandridis:2019:FUR:3308560.3316601,
 author = {Alexandridis, Georgios and Tagaris, Thanos and Siolas, Giorgos and Stafylopatis, Andreas},
 title = {From Free-text User Reviews to Product Recommendation Using Paragraph Vectors and Matrix Factorization},
 booktitle = {Companion Proceedings of The 2019 World Wide Web Conference},
 series = {WWW '19},
 year = {2019},
 isbn = {978-1-4503-6675-5},
 location = {San Francisco, USA},
 pages = {335--343},
 numpages = {9},
 url = {http://doi.acm.org/10.1145/3308560.3316601},
 doi = {10.1145/3308560.3316601},
 acmid = {3316601},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Collaborative Filtering, Free-text reviews, Matrix Factorization, Maximum A-posteriori Estimation, Neural Language Processing, Paragraph Vectors, Recommender Systems, Word2Vec},
} 
</pre>

and

<pre>
@misc{alex2017parvecmf,
    title={ParVecMF: A Paragraph Vector-based Matrix Factorization Recommender System},
    author={Georgios Alexandridis and Georgios Siolas and Andreas Stafylopatis},
    year={2017},
    eprint={1706.07513},
    archivePrefix={arXiv},
    primaryClass={cs.IR}
}
</pre>