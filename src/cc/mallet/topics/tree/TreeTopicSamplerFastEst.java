package cc.mallet.topics.tree;

import cc.mallet.topics.tree.TreeTopicSampler.DocData;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;

public class TreeTopicSamplerFastEst extends TreeTopicSampler{
	
	public TreeTopicSamplerFastEst (int numberOfTopics, double alphaSum, int seed) {
		super(numberOfTopics, alphaSum, seed);
		this.topics = new TreeTopicModelFastEst(this.numTopics, this.random);
	}
	
	public void sampleDoc(int doc_id) {
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
			
			this.changeTopic(doc_id, ii, word, -1, -1);
			
			//double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
			double smoothing_mass_est = this.topics.smoothingEst.get(word);
			double topic_beta_mass = this.topics.computeTermTopicBeta(doc.topicCounts, word);
			
			HIntIntDoubleHashMap topic_term_score = new HIntIntDoubleHashMap();
			double topic_term_mass = this.topics.computeTopicTerm(this.alpha, doc.topicCounts, word, topic_term_score);
			
			double norm_est = smoothing_mass_est + topic_beta_mass + topic_term_mass;
			double sample = this.random.nextDouble();
			//double sample = 0.5;
			sample *= norm_est;

			int new_topic = -1;
			int new_path = -1;
			
			int[] paths = this.topics.getWordPathIndexSet(word);
			
			if (sample < smoothing_mass_est) {
				double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
				double norm =  smoothing_mass + topic_beta_mass + topic_term_mass;
				sample /= norm_est;
				sample *= norm;
				if (sample < smoothing_mass) {
					for (int tt = 0; tt < this.numTopics; tt++) {
						for (int pp : paths) {
							double val = alpha[tt] * this.topics.getPathPrior(word, pp);
							val /= this.topics.getNormalizer(tt, pp);
							sample -= val;
							if (sample <= 0.0) {
								new_topic = tt;
								new_path = pp;
								break;
							}
						}
						if (new_topic >= 0) {
							break;
						}
					}
					myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling smoothing!");
				} else {
					sample -= smoothing_mass;
				}
			} else {
				sample -= smoothing_mass_est;
			}
			
			if (new_topic < 0 && sample < topic_beta_mass) {
				for(int tt : doc.topicCounts.keys()) {
					for (int pp : paths) {
						double val = doc.topicCounts.get(tt) * this.topics.getPathPrior(word, pp);
						val /= this.topics.getNormalizer(tt, pp);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic beta!");
			} else {
				sample -= topic_beta_mass;
			}
			
			if (new_topic < 0) {
				int[] topic_set = topic_term_score.getKey1Set();
				for (int tt : topic_set) {
					int[] path_set = topic_term_score.get(tt).keys();
					for (int pp : path_set) {
						double val = topic_term_score.get(tt, pp);
						//System.out.println(tt + " " + pp + " " + val);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic term!");
			}
			
			this.changeTopic(doc_id, ii, word, new_topic, new_path);
		}
		
	}
	
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords) {
		if(this.topics instanceof TreeTopicModelFastEst) {
			TreeTopicModelFastEst tmp = (TreeTopicModelFastEst) this.topics;
			tmp.computeSmoothingEst(this.alpha);
		}
		super.estimate(numIterations, outputFolder, outputInterval, topWords);
	}
	
}
