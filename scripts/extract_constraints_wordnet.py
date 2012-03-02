from topicmod.util.wordnet import load_wn
from nltk.corpus.reader.wordnet import WordNetError
from topicmod.util import flags
from collections import defaultdict
import codecs

pos_tags = ["n", "v", "a", "r"]

def readVocab(vocabname):
  infile = open(vocabname, 'r')
  vocab = defaultdict(dict)
  for line in infile:
    line = line.strip()
    ww = line.split('\t')
    vocab[ww[0]][ww[1]] = 1
  infile.close()
  return vocab

def generateCons(vocab, wn, outfilename):
  lang = '0'
  cons = defaultdict(dict)
  for word in vocab[lang]:
    for pos in pos_tags:
      synsets = wn.synsets(word, pos)
      for syn in synsets:
        if not syn.offset in cons[pos]:
          cons[pos][syn.offset] = set()
        cons[pos][syn.offset].add(word)

  outfile = codecs.open(outfilename, 'w', 'utf-8')
  for pos in cons:
    for syn in cons[pos]:
      if len(cons[pos][syn]) > 1:
        words = list(cons[pos][syn])
        tmp = "\t".join(words)
        outfile.write("MERGE_\t" + tmp + "\n")
  outfile.close()
  


flags.define_string("vocab", None, "The input vocab")
flags.define_string("output", None, "The output constraint file")

if __name__ == "__main__":

  flags.InitFlags()
  wordnet_path = "../../../data/wordnet/" 
  eng_wn = load_wn("3.0", wordnet_path, "wn")
  vocab = readVocab(flags.vocab)
  generateCons(vocab, eng_wn, flags.output)
  
