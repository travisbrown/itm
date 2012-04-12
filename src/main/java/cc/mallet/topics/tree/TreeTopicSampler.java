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
public interface TreeTopicSampler {
	
	public void initialize(String treeFiles, String hyperFile, String vocabFile);
	public void setNumIterations(int iters);
	//public int getNumIterations();
	//public void resumeLHood(String lhoodFile) throws IOException;
	public void resume(InstanceList training, String resumeDir);
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords);
	//public double lhood();
	//public void report(String outputDir, int topWords) throws IOException;
	//public void printTopWords(File file, int numWords) throws IOException;
	//public String displayTopWords (int numWords);
	//public void printState (File file) throws IOException;
	//public void printStats (File file) throws IOException;
	//public void loadVocab(String vocabFile);
	//public void loadConstraints(String consFile);
	
	public void addInstances(InstanceList training);
	//public void resumeStates(InstanceList training, String statesFile) throws IOException;
	public void clearTopicAssignments(String option, String consFile);
	//public void changeTopic(int doc, int index, int word, int new_topic, int new_path);
	//public double docLHood();
	//public void printDocumentTopics (File file) throws IOException;
	//public void sampleDoc(int doc);
}
