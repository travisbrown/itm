from glob import glob
from topicmod.util import flags
from topicmod.corpora.proto.corpus_pb2 import *
from topicmod.corpora.proto.wordnet_file_pb2 import *
from collections import defaultdict
from nltk import FreqDist
import codecs


def gen_files(proto_corpus_dir, output_dir, lemma_flag):

  doc_num = 0
  vocab = defaultdict(dict)
  tfidf = defaultdict(FreqDist)
  frequency = defaultdict(FreqDist)

  for ii in glob("%s/*.index" % proto_corpus_dir):
    inputfile = open(ii, 'rb')
    protocorpus = Corpus()
    protocorpus.ParseFromString(inputfile.read())
    
    if lemma_flag:
      source = protocorpus.lemmas
    else:
      source = protocorpus.tokens

    for ii in source:
      lang = ii.language
      for jj in ii.terms:
          if jj.id in vocab[lang]:
            assert vocab[lang][jj.id] == jj.original
          else:
            vocab[lang][jj.id] = jj.original
      print len(vocab[lang])

    for dd in protocorpus.doc_filenames:
      doc_num += 1
      if doc_num % 1000 == 0:
        print "Finished reading", doc_num, "documents."

      docfile = open("%s/%s" % (proto_corpus_dir, dd), 'rb')
      doc = Document()
      doc.ParseFromString(docfile.read())
      lang = doc.language
      outputstring = ""

      for jj in doc.sentences:
        for kk in jj.words:
          if lemma_flag:
            word = vocab[lang][kk.lemma]
            tfidf[lang].inc(kk.lemma, kk.tfidf)
            frequency[lang].inc(kk.lemma)
          else:
            word = vocab[lang][kk.token]
            tfidf[lang].inc(kk.token, kk.tfidf)
            frequency[lang].inc(kk.token)

          outputstring += word + " "
      outputstring = outputstring.strip()
      outputstring += "\n"
      
      outputfilename = dd.split('/');
      outputfilename = output_dir + "/" + outputfilename[-1]
      outputfile = open(outputfilename, 'w')
      outputfile.write(outputstring)
      outputfile.close()

    inputfile.close()
  
  return vocab, tfidf, frequency


def gen_vocab(vocab, tfidf, frequency, select_tfidf, outputname, vocab_limit, freq_limit):

  for ii in tfidf:
    for jj in tfidf[ii]:
      tfidf[ii][jj] /= frequency[ii][jj]

  if select_tfidf:
    rank = tfidf
  else:
    rank = frequency

  o = codecs.open(outputname, 'w', 'utf-8')
  for ii in rank:
    count = 0
    for jj in rank[ii]:
      count += 1
      if count <= vocab_limit and frequency[ii][jj] >= freq_limit:
        word = vocab[ii][jj]
        o.write(u"%i\t%s\t%f\t%i\n" % (ii, word, tfidf[ii][jj], frequency[ii][jj]))
        
  o.close()


flags.define_string("proto_corpus", None, "The proto files")
flags.define_bool("lemma", False, "Use lemma or tokens")
flags.define_bool("select_tfidf", False, "select the vocab by tfidf or frequency")
flags.define_string("output", "", "Where we output the preprocessed data")
flags.define_string("vocab", None, "Where we output the vocab")
flags.define_int("vocab_limit", 10000, "The vocab size")
flags.define_int("freq_limit", 20, "The minimum frequency of each word")

if __name__ == "__main__":

  flags.InitFlags()  
  [vocab, tfidf, frequency] = gen_files(flags.proto_corpus, flags.output, flags.lemma)
  gen_vocab(vocab, tfidf, frequency, flags.select_tfidf, flags.vocab, flags.vocab_limit, flags.freq_limit)

