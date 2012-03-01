from topicmod.util import flags
from collections import defaultdict
from nltk import FreqDist
import codecs

def readStats(filename):
  tfidf = defaultdict(FreqDist)
  frequency = defaultdict(FreqDist)
  infile = open(filename, 'r')
  for line in infile:
    line = line.strip()
    ww = line.split('\t')
    tfidf[ww[0]].inc(ww[1], float(ww[2]))
    frequency[ww[0]].inc(ww[1], int(ww[3]))

  infile.close()
  return tfidf, frequency

def sortVocab(infilename, tfidf, frequency, option, outfilename):
  if option == 1:
    source = tfidf
  else:
    source = frequency

  infile = open(infilename, 'r')
  vocab = defaultdict(FreqDist)
  for line in infile:
    line = line.strip()
    ww = line.split('\t')
    lang = ww[0]
    vocab[lang].inc(ww[1], source[lang][ww[1]])
  infile.close()

  outfile = codecs.open(outfilename, 'w', 'utf-8')
  for ii in vocab:
    for jj in vocab[ii]:
      outfile.write(u"%i\t%s\t%f\t%i\n" % (ii, jj, tfidf[ii][jj], frequency[ii][jj]))
  outfile.close()


flags.define_string("stats_vocab", None, "The proto files")
flags.define_string("input_vocab", None, "Where we get the original vocab")
flags.define_int("option", 0, "1: tfidf; others: frequency")
flags.define_string("sorted_vocab", None, "Where we output the vocab")

if __name__ == "__main__":

  flags.InitFlags()  
  [tfidf, frequency] = readStats(flags.stats_vocab)
  
  sortVocab(flags.input_vocab, tfidf, frequency, flags.option, flags.sorted_vocab)
