package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * This class defines the tree topic sampler, which loads the instances,  
 * reports the topics, and leaves the sampler method as an abstract method, 
 * which might be various for different methods.
 * Author: Yuening Hu
 */
public abstract class TreeTopicSampler {
	
	/**
	 * This class defines the format of a document.
	 */
	public class DocData {
		TIntArrayList tokens;
		TIntArrayList topics;
		TIntArrayList paths;
		// sort
		TIntIntHashMap topicCounts;
		String docName;
		
		public DocData (String name, TIntArrayList tokens, TIntArrayList topics,
				TIntArrayList paths, TIntIntHashMap topicCounts) {
			this.docName = name;
			this.tokens = tokens;
			this.topics = topics;
			this.paths = paths;
			this.topicCounts = topicCounts;
		}
		
		public String toString() {
			String result = "***************\n";
			result += docName + "\n";
			
			result += "tokens:   ";
			for (int jj = 0; jj < tokens.size(); jj++) {
				int index = tokens.get(jj);
				String word = vocab.get(index);
				result += word + " " + index + ", ";
			}
			
			result += "\ntopics:   ";
			result += topics.toString();
			
			result += "\npaths:    ";
			result += paths.toString();
			
			result += "\ntopicCounts:   ";

			for(TIntIntIterator it = this.topicCounts.iterator(); it.hasNext(); ) {
				it.advance();
				result += "Topic " + it.key() + ": " + it.value() + ", ";
			}
			result += "\n*****************\n";
			return result;
		}
	}
	
	int numTopics; // Number of topics to be fit
	int numIterations;
	int startIter;
	Randoms random;
	double[] alpha;
	double alphaSum;
	TDoubleArrayList lhood;
	TDoubleArrayList iterTime;
	ArrayList<String> vocab;
	ArrayList<DocData> data;
	TreeTopicModel topics;
	TIntHashSet cons;
	
	public TreeTopicSampler (int numberOfTopics, double alphaSum, int seed) {
		this.numTopics = numberOfTopics;
		this.random = new Randoms(seed);

		this.alphaSum = alphaSum;
		this.alpha = new double[numTopics];
		Arrays.fill(alpha, alphaSum / numTopics);

		this.data = new ArrayList<DocData> ();
		this.vocab = new ArrayList<String> ();
		this.cons = new TIntHashSet();
		
		this.lhood = new TDoubleArrayList();
		this.iterTime = new TDoubleArrayList();
		this.startIter = 0;
		
		// notice: this.topics is not initialized in this abstract class,
		// in each sub class, the topics variable is initialized differently.
	}
	
	/**
	 * This function loads vocab, loads tree, and initialize parameters.
	 */
	public void initialize(String treeFiles, String hyperFile, String vocabFile) {
		this.loadVocab(vocabFile);
		this.topics.initializeParams(treeFiles, hyperFile, this.vocab);
	}
	
	public void setNumIterations(int iters) {
		this.numIterations = iters;
	}
	
	public int getNumIterations() {
		return this.numIterations;
	}
	
	/**
	 * This function adds instances given the training data in mallet input data format.
	 * For each token in a document, sample a topic and then sample a path based on prior. 
	 */
	public void addInstances(InstanceList training) {
		boolean debug = false;
		int count = 0;
		for (Instance instance : training) {
			count++;
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			String name = instance.getName().toString();

			// *** remained problem: keep topicCounts sorted
			TIntArrayList tokens = new TIntArrayList(original_tokens.getLength());
			TIntIntHashMap topicCounts = new TIntIntHashMap ();			
			TIntArrayList topics = new TIntArrayList(original_tokens.getLength());
			TIntArrayList paths = new TIntArrayList(original_tokens.getLength());

			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				int token = this.vocab.indexOf(word);
				int topic = random.nextInt(numTopics);
				if(debug) { topic = count % numTopics; }
				tokens.add(token);
				topics.add(topic);
				topicCounts.adjustOrPutValue(topic, 1, 1);
				// sample a path for this topic
				int path_index = this.topics.initialize(token, topic);
				paths.add(path_index);
			}
			
			DocData doc = new DocData(name, tokens, topics, paths, topicCounts);
			this.data.add(doc);
			
			//System.out.println(doc);
		}
		
	}
	
	/**
	 * Resume instance states from the saved states file. 
	 */
	public void resumeStates(InstanceList training, String statesFile) throws IOException{
		FileInputStream statesfstream = new FileInputStream(statesFile);
		DataInputStream statesdstream = new DataInputStream(statesfstream);
		BufferedReader states = new BufferedReader(new InputStreamReader(statesdstream));
			
		// reading topics, paths
		for (Instance instance : training) {
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			String name = instance.getName().toString();

			// *** remained problem: keep topicCounts sorted
			TIntArrayList tokens = new TIntArrayList(original_tokens.getLength());
			TIntIntHashMap topicCounts = new TIntIntHashMap ();			
			TIntArrayList topics = new TIntArrayList(original_tokens.getLength());
			TIntArrayList paths = new TIntArrayList(original_tokens.getLength());
			
			//
			String statesLine = states.readLine();
			myAssert(statesLine != null, "statesFile doesn't match with the training data");
			statesLine = statesLine.trim();
			String[] str = statesLine.split("\t");
			myAssert(str.length == original_tokens.getLength(), "resume problem!");

			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				int token = this.vocab.indexOf(word);
				String[] tp = str[jj].split(":");
				myAssert(tp.length == 2, "statesFile problem!");
				int topic = Integer.parseInt(tp[0]);
				int path = Integer.parseInt(tp[1]);
				tokens.add(token);
				topics.add(topic);
				paths.add(path);
				topicCounts.adjustOrPutValue(topic, 1, 1);
				this.topics.changeCountOnly(topic, token, path, 1);
			}
			
			DocData doc = new DocData(name, tokens, topics, paths, topicCounts);
			this.data.add(doc);
		}
		states.close();
	}
	
	/**
	 * Resume lhood and iterTime from the saved lhood file. 
	 */
	public void resumeLHood(String lhoodFile) throws IOException{
		FileInputStream lhoodfstream = new FileInputStream(lhoodFile);
		DataInputStream lhooddstream = new DataInputStream(lhoodfstream);
		BufferedReader brLHood = new BufferedReader(new InputStreamReader(lhooddstream));
		// the first line is the title
		String strLine = brLHood.readLine();
		while ((strLine = brLHood.readLine()) != null) {
			strLine = strLine.trim();
			String[] str = strLine.split("\t");
			// iteration, likelihood, iter_time
			myAssert(str.length == 3, "lhood file problem!");
			this.lhood.add(Double.parseDouble(str[1]));
			this.iterTime.add(Double.parseDouble(str[2]));
		}
		this.startIter = this.lhood.size();
		if (this.startIter > this.numIterations) {
			System.out.println("Have already sampled " + this.numIterations + " iterations!");
			System.exit(0);
		}
		System.out.println("Start sampling for iteration " + this.startIter);
		brLHood.close();
	}
	
	/**
	 * Resumes from the saved files.
	 */
	public void resume(InstanceList training, String resumeDir) {
		try {
			String statesFile = resumeDir + ".states";
			resumeStates(training, statesFile);
			
			String lhoodFile = resumeDir + ".lhood";
			resumeLHood(lhoodFile);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * This function clears the topic and path assignments for some words:
	 * (1) term option: only clears the topic and path for constraint words;
	 * (2) doc option: clears the topic and path for documents which contain 
	 *     at least one of the constraint words.
	 */
	public void clearTopicAssignments(String option, String consFile) {
		this.loadConstraints(consFile);
		if (this.cons == null || this.cons.size() <= 0) {
			return;
		}
		
		for(int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			Boolean flag = false;
			for(int ii = 0; ii < doc.tokens.size(); ii++) {
				int word = doc.tokens.get(ii);
				if(this.cons.contains(word)) {
					if (option.equals("term")) {
						int topic = doc.topics.get(ii);
						int path = doc.paths.get(ii);
						// change the count for count and node_count in TopicTreeWalk
						this.topics.changeCountOnly(topic, word, path, -1);
						doc.topics.set(ii, -1);
						doc.paths.set(ii, -1);
						myAssert(doc.topicCounts.get(topic) >= 1, "clear topic assignments problem");
						doc.topicCounts.adjustValue(topic, -1);
					} else if (option.equals("doc")) {
						flag = true;
						break;
					}
				}
			}
			if (flag) {
				for(int ii = 0; ii < doc.tokens.size(); ii++) {
					int word = doc.tokens.get(ii);
					int topic = doc.topics.get(ii);
					int path = doc.paths.get(ii);
					this.topics.changeCountOnly(topic, word, path, -1);
					doc.topics.set(ii, -1);
					doc.paths.set(ii, -1);
				}
				doc.topicCounts.clear();
			}
		}
	}
	
	/**
	 * This function defines how to change a topic during the sampling process.
	 * It handles the case where both new_topic and old_topic are "-1" (empty topic).
	 */
	public void changeTopic(int doc, int index, int word, int new_topic, int new_path) {
		DocData current_doc = this.data.get(doc);
		int old_topic = current_doc.topics.get(index);
		int old_path = current_doc.paths.get(index);
		
		if (old_topic != -1) {
			myAssert((new_topic == -1 && new_path == -1), "old_topic != -1 but new_topic != -1");
			this.topics.changeCount(old_topic, word, old_path, -1);
			myAssert(current_doc.topicCounts.get(old_topic) > 0, "Something wrong in changTopic");
			current_doc.topicCounts.adjustValue(old_topic, -1);
			current_doc.topics.set(index, -1);
			current_doc.paths.set(index, -1);
		}
		
		if (new_topic != -1) {
			myAssert((old_topic == -1 && old_path == -1), "new_topic != -1 but old_topic != -1");
			this.topics.changeCount(new_topic, word, new_path, 1);
			current_doc.topicCounts.adjustOrPutValue(new_topic, 1, 1);
			current_doc.topics.set(index, new_topic);
			current_doc.paths.set(index, new_path);
		}
	}
	
	/**
	 * This function defines the sampling process, computes the likelihood and running time,
	 * and specifies when to save the states files.
	 */
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords) {
		// update parameters
		this.topics.updateParams();
		for (int ii = this.startIter; ii <= numIterations; ii++) {
			long starttime = System.currentTimeMillis();
			//System.out.println("Iter " + ii);
			for (int dd = 0; dd < this.data.size(); dd++) {
				this.sampleDoc(dd);
				if (dd > 0 && dd % 1000 == 0) {
					System.out.println("Sampled " + dd + " documents.");
				}
			}
			
			double totaltime = (double)(System.currentTimeMillis() - starttime) / 1000;
			double lhood = this.lhood();
			this.lhood.add(lhood);
			this.iterTime.add(totaltime);
			
			String tmp = "Iteration " + ii;
			tmp += " likelihood " + lhood;
			tmp += " totaltime " + totaltime;
			System.out.println(tmp);
			
			if ((ii > 0 && ii % outputInterval == 0) || ii == numIterations) {
				try {
					this.report(outputFolder, topWords);
				} catch (IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}
	
	/**
	 * The function computes the document likelihood.
	 */
	public double docLHood() {
		int docNum = this.data.size();
		
		double val = 0.0;
		val += Dirichlet.logGamma(this.alphaSum) * docNum;
		double tmp = 0.0;
		for (int tt = 0; tt < this.numTopics; tt++) {
			tmp += Dirichlet.logGamma(this.alpha[tt]);
		}
		val -= tmp * docNum;
		for (int dd = 0; dd < docNum; dd++) {
			DocData doc = this.data.get(dd);
			for (int tt = 0; tt < this.numTopics; tt++) {
				val += Dirichlet.logGamma(this.alpha[tt] + doc.topicCounts.get(tt));
			}
			val -= Dirichlet.logGamma(this.alphaSum + doc.topics.size());
		}
		return val;
	}
	
	/**
	 * This function returns the likelihood.
	 */
	public double lhood() {
		return this.docLHood() + this.topics.topicLHood();
	}
	
	/**
	 * This function reports the detected topics, the documents topics,
	 * and saves states file and lhood file.
	 */
	public void report(String outputDir, int topWords) throws IOException {

		String topicKeysFile = outputDir + ".topics";
		this.printTopWords(new File(topicKeysFile), topWords);
		
		String docTopicsFile = outputDir + ".docs";
		this.printDocumentTopics(new File(docTopicsFile));
		
		String stateFile = outputDir + ".states";
		this.printState (new File(stateFile));
		
		String statsFile = outputDir + ".lhood";
		this.printStats (new File(statsFile));
	}
	
	/**
	 * This function prints the topic words of each topic.
	 */
	public void printTopWords(File file, int numWords) throws IOException {
		PrintStream out = new PrintStream (file);
		out.print(displayTopWords(numWords));
		out.close();
	}
	
	/**
	 * By implementing the comparable interface, this function ranks the words
	 * in each topic, and returns the top words for each topic.
	 */
	public String displayTopWords (int numWords) {
		
		class WordProb implements Comparable {
			int wi;
			double p;
			public WordProb (int wi, double p) { this.wi = wi; this.p = p; }
			public final int compareTo (Object o2) {
				if (p > ((WordProb)o2).p)
					return -1;
				else if (p == ((WordProb)o2).p)
					return 0;
				else return 1;
			}
		}

		StringBuilder out = new StringBuilder();
		int numPaths = this.topics.getPathNum();
		//System.out.println(numPaths);
		
		for (int tt = 0; tt < this.numTopics; tt++){
			String tmp = "\n--------------\nTopic " + tt + "\n------------------------\n";
			//System.out.print(tmp);
			out.append(tmp);
			WordProb[] wp = new WordProb[numPaths];
			for (int pp = 0; pp < numPaths; pp++){
				int ww = this.topics.getWordFromPath(pp);
				double val = this.topics.computeTopicPathProb(tt, ww, pp);
				wp[pp] = new WordProb(pp, val);
			}
			Arrays.sort(wp);
			for (int ii = 0; ii < wp.length; ii++){
				int pp = wp[ii].wi;
				int ww = this.topics.getWordFromPath(pp);
				//tmp = wp[ii].p + "\t" + this.vocab.lookupObject(ww) + "\n";
				tmp = wp[ii].p + "\t" + this.vocab.get(ww) + "\n";
				//System.out.print(tmp);
				out.append(tmp);
				if(ii > numWords) {
					break;
				}
			}	
		}	
		return out.toString();
	}
	
	/**
	 * Prints the index, original document dir, topic counts for each document.
	 */
	public void printDocumentTopics (File file) throws IOException {
		PrintStream out = new PrintStream (file);
		
		for (int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			String tmp = dd + "\t" + doc.docName + "\t";
			for (int tt : doc.topicCounts.keys()) {
				int count = doc.topicCounts.get(tt);
				tmp += tt + ":" + count + "\t";
			}
			out.print(tmp + "\n");
		}
		out.close();
	}
	
	/**
	 * Prints the topic and path of each word for all documents.
	 */
	public void printState (File file) throws IOException {
		//PrintStream out =
		//	new PrintStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))));
		PrintStream out = new PrintStream(file);
		
		for (int dd = 0; dd < this.data.size(); dd++) {
			DocData doc = this.data.get(dd);
			String tmp = "";
			for (int ww = 0; ww < doc.topics.size(); ww++) {
				int topic = doc.topics.get(ww);
				int path = doc.paths.get(ww);
				tmp += topic + ":" + path + "\t";
			}
			out.println(tmp);
		}
		out.close();
	}
	
	/**
	 * Prints likelihood and iter time.
	 */
	public void printStats (File file) throws IOException {
		PrintStream out = new PrintStream (file);
		String tmp = "Iteration\t\tlikelihood\titer_time\n";
		out.print(tmp);
		for (int iter = 0; iter < this.lhood.size(); iter++) {
			tmp = iter + "\t" + this.lhood.get(iter) + "\t" + this.iterTime.get(iter);
			out.println(tmp);
		}
		out.close();
	}
	
	/**
	 * Load vocab
	 */
	public void loadVocab(String vocabFile) {
				
		try {
			FileInputStream infstream = new FileInputStream(vocabFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split("\t");
				if (str.length > 1) {
					this.vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}

	}
	
	/**
	 * Load constraints
	 */
	public void loadConstraints(String consFile) {
		try {
			FileInputStream infstream = new FileInputStream(consFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split("\t");
				if (str.length > 1) {
					// str[0] is either "MERGE_" or "SPLIT_", not a word
					for(int ii = 1; ii < str.length; ii++) {
						int word = this.vocab.indexOf(str[ii]);
						myAssert(word >= 0, "Constraint words not found in vocab: " + str[ii]);
						cons.add(word);
					}
					this.vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}

	}
	
	/**
	 * For testing~~
	 */
	public static void myAssert(boolean flag, String info) {
		if(!flag) {
			System.out.println(info);
			System.exit(0);
		}
	}
	
	abstract void sampleDoc(int doc);
}
