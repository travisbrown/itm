Playing with interactive tree topic modeling~~

(0) cd javaldawn/tree-TM

(1) Compile src code
javac -cp class:lib/* src/cc/mallet/topics/*/*.java -d class

(2) Read each documents and save in mallet input format:
bin/mallet import-dir --input ../../../data/synthetic/synth_word --output input/synthetic-topic-input.mallet --keep-sequence

* Notice: you can add --remove-stopwords option if you want. But for synthetic data, do not use this option

(3) Generate vocab file after mallet preprocessing:
bin/mallet train-topics --input input/synthetic-topic-input.mallet --use-tree-lda true --generate-vocab true --vocab input/synthetic.voc

(4) Generating the tree:
python tree/ontology_writer_wordleaf.py --vocab=input/synthetic.voc --constraints=input/empty.cons --write_wordnet=False --write_constraints=True --wnname=input/synthetic.wn

* Notice: the constraints file can be empty, then it is just a tree with symmetric prior, working as the normal LDA

* If you want to check the generated tree structure:
cat input/synthetic/synthetic.wn.0 | protoc tree/lib/proto/wordnet_file.proto --decode=topicmod_projects_ldawn.WordNetFile --proto_path=tree/lib/proto/ > input/synthetic/tmp0.txt
cat input/synthetic/synthetic.wn.1 | protoc tree/lib/proto/wordnet_file.proto --decode=topicmod_projects_ldawn.WordNetFile --proto_path=tree/lib/proto/ > input/synthetic/tmp1.txt

(5) Train tree topic models:
bin/mallet train-topics --input input/synthetic-topic-input.mallet --num-topics 5 --num-iterations 300 --alpha 0.5 --random-seed 0 --output-interval 10 --output-dir output/model --use-tree-lda True --tree input/synthetic.wn --tree-hyperparameters input/tree_hyperparams --vocab input/synthetic.voc --clear-type term --constraint input/empty.cons

(6) Resume tree topic models:
bin/mallet train-topics --input input/synthetic-topic-input.mallet --num-topics 5 --num-iterations 600 --alpha 0.5 --random-seed 0 --output-interval 10 --output-dir output/model --use-tree-lda True --tree input/synthetic.wn --tree-hyperparameters input/tree_hyperparams --vocab input/synthetic.voc --clear-type term --constraint input/empty.cons --resume true --resume-dir output/model


*** Notice ***
This implementation of tree-based topic modeling is following the framework of mallet package.
The code can be easily merged to mallet by:
* move "cc.mallet.topics.tree" folder to the mallet src folder cc.mallet.topics
* merge "Verctor2Topics.java" with the one with the same title in mallet (cc.mallet.topics.tui.Vector2Topics.java): simply by adding the new command opitions required by tree topic modeling and another if statement (else if (useTreeLDA.value) {...})
* adding new lib file: wordnet.jar
