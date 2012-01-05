/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.topics.tui;

import cc.mallet.util.CommandOption;
import cc.mallet.util.Randoms;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.LabelSequence;
import cc.mallet.topics.*;
import cc.mallet.topics.tree.GenerateVocab;
import cc.mallet.topics.tree.PriorTree;
import cc.mallet.topics.tree.TreeTopicSampler;
import cc.mallet.topics.tree.TreeTopicSamplerFast;
import cc.mallet.topics.tree.TreeTopicSamplerFastEst;
import cc.mallet.topics.tree.TreeTopicSamplerFastSort;
import cc.mallet.topics.tree.TreeTopicSamplerNaive;
import cc.mallet.topics.tree.TreeTopicSamplerSort;

import java.io.*;

/** Perform topic analysis in the style of LDA and its variants.
 *  @author <a href="mailto:mccallum@cs.umass.edu">Andrew McCallum</a>
 */

public class Vectors2Topics {

	// common options in mallet
	static CommandOption.String inputFile = new CommandOption.String
		(Vectors2Topics.class, "input", "FILENAME", true, null,
		 "The filename from which to read the list of training instances.  Use - for stdin.  " +
		 "The instances must be FeatureSequence or FeatureSequenceWithBigrams, not FeatureVector", null);

	static CommandOption.Integer numTopics = new CommandOption.Integer
		(Vectors2Topics.class, "num-topics", "INTEGER", true, 10,
		 "The number of topics to fit.", null);

	static CommandOption.Integer numIterations = new CommandOption.Integer
		(Vectors2Topics.class, "num-iterations", "INTEGER", true, 1000,
		 "The number of iterations of Gibbs sampling.", null);

	static CommandOption.Integer randomSeed = new CommandOption.Integer
		(Vectors2Topics.class, "random-seed", "INTEGER", true, 0,
		 "The random seed for the Gibbs sampler.  Default is 0, which will use the clock.", null);

	static CommandOption.Integer topWords = new CommandOption.Integer
		(Vectors2Topics.class, "num-top-words", "INTEGER", true, 20,
		 "The number of most probable words to print for each topic after model estimation.", null);

	static CommandOption.Double alpha = new CommandOption.Double
		(Vectors2Topics.class, "alpha", "DECIMAL", true, 50.0,
		 "Alpha parameter: smoothing over topic distribution.",null);

	////////////////////////////////////
	// new options
	
	static CommandOption.Boolean useTreeLDA = new CommandOption.Boolean
	(Vectors2Topics.class, "use-tree-lda", "true|false", false, false,
	 "Rather than using flat prior for LDA, use the tree-based prior for LDA, which models words' correlations." +
	 "You cannot do this and also --use-ngrams or --use-PAM.", null);
	
	static CommandOption.String modelType = new CommandOption.String
	(Vectors2Topics.class, "tree-model-type", "TYPENAME", true, "fast-est",
	 "Three possible types: naive, fast, fast-est, fast-sort.", null);

	static CommandOption.String treeFiles = new CommandOption.String
	(Vectors2Topics.class, "tree", "FILENAME", true, null,
	 "The input files for tree structure.", null);
	
	static CommandOption.String hyperFile = new CommandOption.String
	(Vectors2Topics.class, "tree-hyperparameters", "FILENAME", true, null,
	 "The hyperparameters for tree structure.", null);
	
	static CommandOption.String vocabFile = new CommandOption.String
	(Vectors2Topics.class, "vocab", "FILENAME", true, null,
	 "The input vocabulary.", null);
	
	static CommandOption.String consFile = new CommandOption.String
	(Vectors2Topics.class, "constraint", "FILENAME", true, null,
	"The input constraint file.", null);
	
	static CommandOption.Integer outputInteval = new CommandOption.Integer
	(Vectors2Topics.class, "output-interval", "INTEGER", true, 20,
	 "For each interval, the result files are output to the outputFolder.", null);
	
	static CommandOption.String outputDir= new CommandOption.String
	(Vectors2Topics.class, "output-dir", "FOLDERNAME", true, null,
	 "The output folder.", null);
	
	static CommandOption.Boolean resume = new CommandOption.Boolean
	(Vectors2Topics.class, "resume", "true|false", false, false,
	 "Resume from the previous output states.", null);
	
	static CommandOption.String resumeDir = new CommandOption.String
	(Vectors2Topics.class, "resume-dir", "FOLDERNAME", true, null,
	 "The resume folder.", null);
	
	static CommandOption.String clearType = new CommandOption.String
	(Vectors2Topics.class, "clear-type", "TYPENAME", true, null,
	 "Two possible types: doc, term.", null);
	
        static CommandOption.Boolean genVocab = new CommandOption.Boolean
	    (Vectors2Topics.class, "generate-vocab", "true|false", false, false,
	     "Generate vocab after mallet preprocessing.", null);
	
        static CommandOption.Integer bubble = new CommandOption.Integer
	(Vectors2Topics.class, "bubble", "INTEGER", true, -1,
	 "bubble sort options: -2:treemap, -1:bubble, 0:bubble0, 1:bubble1, 2:bubbl2, 3:bubble3, others:fast.", null);	
	
	public static void main (String[] args) throws java.io.IOException
	{
		// Process the command-line options
		CommandOption.setSummary (Vectors2Topics.class,
								  "A tool for estimating, saving and printing diagnostics for topic models, such as LDA.");
		CommandOption.process (Vectors2Topics.class, args);

		if (useTreeLDA.value) {
			InstanceList ilist = InstanceList.load (new File(inputFile.value));
			System.out.println ("Data loaded.");
			
			if (genVocab.value) {
				GenerateVocab.genVocab(ilist, vocabFile.value);
			} else {
				TreeTopicSampler topicModel = null;
				if (modelType.value.equals("naive")) {
					topicModel = new TreeTopicSamplerNaive(
							numTopics.value, alpha.value, randomSeed.value);
				} else if (modelType.value.equals("fast")){
					topicModel = new TreeTopicSamplerFast(
							numTopics.value, alpha.value, randomSeed.value, bubble.value);
				} else if (modelType.value.equals("fast-est")) {
					topicModel = new TreeTopicSamplerFastEst(
							numTopics.value, alpha.value, randomSeed.value);
				} else {
					System.out.println("model type wrong! please use " +
							"'naive' 'fast' or 'fast-est'!");
					System.exit(0);
				}
				
//				TreeTopicSamplerSort topicModel = new TreeTopicSamplerFastSort(
//						numTopics.value, alpha.value, randomSeed.value);
				
				// load tree and vocab
				topicModel.initialize(treeFiles.value, hyperFile.value, vocabFile.value);
	            topicModel.setNumIterations(numIterations.value);
	            
				if (resume.value == true) {
					// resume instances from the saved states
					topicModel.resume(ilist, resumeDir.value);
				} else {
					// add instances
					topicModel.addInstances(ilist);
				}
				
				// if clearType is not null, clear the topic assignments of the 
				// constraint words
				if (clearType.value != null) {
					if (clearType.value.equals("term") || clearType.value.equals("doc")) {
						topicModel.clearTopicAssignments(clearType.value, consFile.value);
					} else {
						System.out.println("clear type wrong! please use either 'doc' or 'term'!");
						System.exit(0);
					}
				}
				
				// sampling and save states
				topicModel.estimate(numIterations.value, outputDir.value, 
									outputInteval.value, topWords.value);
				
				// topic report
				//System.out.println(topicModel.displayTopWords(topWords.value));
			}
		}		
	}

}
