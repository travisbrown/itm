Interactive tree topic modeling
===============================

Yuening Hu, Jordan Boyd-Graber, and Brianna Satinoff.
[Interactive Topic Modeling](http://umiacs.umd.edu/~jbg/docs/itm.pdf).
_Association for Computational Linguistics_, 2011.

Topic models have been used extensively as a tool for corpus exploration, and
a cottage industry has developed to tweak topic models to better encode human
intuitions or to better model data. However, creating such extensions requires
expertise in machine learning unavailable to potential end-users of topic
modeling software. In this work, we develop a framework for allowing users to
iteratively refine the topics discovered by models such as latent Dirichlet
allocation (LDA) by adding constraints that enforce that sets of words must
appear together in the same topic.

------------------------------------------------------------------------------

The project code has been Mavenified and lightly edited by Travis Brown. All
dependencies are now managed by Maven and are not packaged with the project.
The Java source files have also been moved out of the MALLET namespace and it
is no longer necessary to merge them manually with the MALLET source.

Compiling
---------

[Apache Maven](http://maven.apache.org/) is required to build this project.
The following command will download all dependencies as necessary and compile
the code:

    mvn compile

The class files will now be available in `target/classes`, and will be used
when you run the `bin/mallet` script in subsequent steps.

Importing documents
-------------------

The following command will convert documents into the MALLET format as
described [in the MALLET documentation](http://mallet.cs.umass.edu/import.php):

    bin/mallet import-dir --input ../../../data/synthetic/synth_word \
      --output input/synthetic-topic-input.mallet --keep-sequence

Note that for this synthetic data we do not use `--remove-stopwords`, but in
general you would want to include it here. Note also that the `input`
directory contains the `synthetic-topic-input.mallet` file, so you can skip
this step and continue directly to the steps below.

Generating vocabulary file
--------------------------

    bin/mallet train-topics --input input/synthetic-topic-input.mallet \
      --use-tree-lda true --generate-vocab true --vocab input/synthetic/synthetic.voc

Generating the tree
-------------------

The following command requires Python 2, so you may need to change the
`python` command if Python 3 is the default on your system.

    python tree/ontology_writer_wordleaf.py --vocab=input/synthetic/synthetic.voc \
      --constraints=input/empty.cons --write_wordnet=False \
      --write_constraints=True --wnname=input/synthetic/synthetic.wn

Note that the constraints file can be empty, in which case the output is a
tree with symmetric priors, working as in normal LDA.

You can check the generated tree structure with the following commands (note
that Protobuf 2.3 is required):

    cat input/synthetic/synthetic.wn.0 | protoc tree/lib/proto/wordnet_file.proto \
      --decode=topicmod_projects_ldawn.WordNetFile \
      --proto_path=tree/lib/proto/ > input/synthetic/tmp0.txt

    cat input/synthetic/synthetic.wn.1 | protoc tree/lib/proto/wordnet_file.proto \
      --decode=topicmod_projects_ldawn.WordNetFile \
      --proto_path=tree/lib/proto/ > input/synthetic/tmp1.txt

Training the tree topic model
-----------------------------

    bin/mallet train-topics --input input/synthetic-topic-input.mallet --num-topics 5 \
      --num-iterations 300 --alpha 0.5 --random-seed 0 --output-interval 10 \
      --output-dir output/model --use-tree-lda True --tree-model-type fast \
      --tree input/synthetic/synthetic.wn --tree-hyperparameters input/tree_hyperparams \
      --vocab input/synthetic/synthetic.voc --clear-type term --constraint input/empty.cons

Resuming the tree topic model
-----------------------------

    bin/mallet train-topics --input input/synthetic-topic-input.mallet --num-topics 5 \
      --num-iterations 600 --alpha 0.5 --random-seed 0 --output-interval 10 \
      --output-dir output/model --use-tree-lda True \
      --tree input/synthetic/synthetic.wn --tree-hyperparameters input/tree_hyperparams \
      --vocab input/synthetic/synthetic.voc --clear-type term --constraint input/empty.cons \
      --resume true --resume-dir output/model

