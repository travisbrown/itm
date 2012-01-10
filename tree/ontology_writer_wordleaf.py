from collections import defaultdict
from lib.proto.wordnet_file_pb2 import *
from lib.proto.corpus_pb2 import *
from lib import flags
import re

flags.define_string("vocab", "vocab/semcor.voc", \
                      "the vocabulary used for building the tree")
flags.define_string("wnname", "wn/wordnet.wn", "Where we write output")
flags.define_string("constraints", "",
                    "where we get the constraints, " +
                    "one tab-delimited constraint per line")

flags.define_bool("write_constraints", False, "Write out example constraint")
flags.define_bool("write_wordnet", False, "Write out wordnet")
flags.define_bool("write_toy", False, "Write out a toy wordnet")
flags.define_bool("merge_constraints", True, "Put duplicate constraints into" +
                  " a single constraint")


def orderedTraversal(wn, pos='n', limit_depth=-1, reverse_depth=False):
    """
    Given a wordnet object, give the synsets in order of internal nodes first,
    followed by leaves.

    @param pos Which part of speech we search
    @param limit_depth Don't consider nodes deeper than this
    @param reverse Reverse the order of the search (leaves first)
    """

    # Find the max depth synset
    max_depth = max(x.max_depth() for x in wn.all_synsets(pos))
    if limit_depth >= 0:
        max_depth = min(max_depth, limit_depth)

    depths = range(max_depth)
    if reverse_depth:
        depths.reverse()

    for depth in depths:
        for ii in wn.all_synsets(pos):
            assert ii.max_depth() >= 0
            if ii.max_depth() == depth:
                yield ii
    return


class OntologyWriter:

    def __init__(self, filename, propagate_counts=True, max_leaves=10000):
        self.parents_ = defaultdict(set)

        # Keep track of how many output files we've written
        self.num_files_ = 0
        self.max_leaves_ = max_leaves
        self.filename_ = filename

        self.leaf_synsets_ = {}
        self.internal_synsets_ = {}

        self.internal_wn_ = WordNetFile()
        self.leaf_wn_ = WordNetFile()
        self.leaf_wn_.root = -1

        self.root_ = None
        self.vocab_ = defaultdict(dict)
        self.finalized_ = False
        self.propagate_counts_ = propagate_counts

    def parents(self, id):
        p = list(self.parents_[id])
        if len(p) == 0:
            if self.root_ == None:
                self.root_ = id
            assert self.root_ == id, "Both %i and %i appear to be root" \
                   % (id, self.root_)
            return []
        else:
            for ii in self.parents_[id]:
                p += self.parents(ii)
        return p

    def term_id(self, lang, term):
        if not term in self.vocab_[lang]:
            self.vocab_[lang][term] = len(self.vocab_)

        return self.vocab_[lang][term]

    def FindRoot(self, synsets):
        for ii in synsets:
            if ii % 1000 == 0:
                print "Finalizing", ii
            for pp in self.parents(ii):
                if self.propagate_counts_:
                    self.internal_synsets_[pp].hyponym_count += \
                        synsets[ii].raw_count
            assert synsets[ii].hyponym_count > 0, \
                   "Synset %s had zero hyponym" % synsets[ii].key

    def Finalize(self):
        """
        Check topology and percolate counts if that hasn't been done already.
        """
        self.FindRoot(self.leaf_synsets_)
        self.Write(self.leaf_wn_)
        self.FindRoot(self.internal_synsets_)

        assert self.root_ != None, "No root has been found"
        self.internal_wn_.root = self.root_
        self.Write(self.internal_wn_)

    def Write(self, wn_proto):
        filename = self.filename_ + ".%s" % str(self.num_files_)
        f = open(filename, "wb")
        s = wn_proto.SerializeToString()
        f.write(s)
        print "Serialized version ", s[:10], "(", len(s), \
              ") written to", filename
        f.close()
        self.num_files_ += 1

    def AddSynset(self, numeric_id, sense_key, children,
                  words, hyponym_count=0.0):
        """
        @param numeric_id : A unique identifier of the synset (something like
        the offset)
        @param sense_key : A string identifying the synset (e.g. a current WN
        sense key)
        @param children : A list of the identifiers of the synset's children
        @param words : A list of (lang, word, count) tuples
        @param hyponym_count: The count of all the synsets beneath this synset.
        (Note that this does not include the count of the words in this
        argument; that will get added to the count for when it is stored in the
        protocol buffer)
        """

        assert not numeric_id in self.internal_synsets_, \
            "internal %i already added" % numeric_id
        assert not numeric_id in self.leaf_synsets_, \
            "leaf %i already added" % numeric_id

        if len(children) > 0:
            s = self.internal_wn_.synsets.add()
        else:
            s = self.leaf_wn_.synsets.add()

        s.offset = numeric_id
        s.key = sense_key

        if re.search("ML_", sense_key):
            s.hyperparameter = "ML_"
        elif re.search("CL_", sense_key):
            s.hyperparameter = "CL_"
        elif re.search("NL_", sense_key):
            s.hyperparameter = "NL_"
        elif re.search("ROOT", sense_key):
            s.hyperparameter = "NL_"
        elif re.search("LEAF_", sense_key):
            s.hyperparameter = "NL_"
        else:
            s.hyperparameter = "DEFAULT_"

        for ii in children:
            self.parents_[ii].add(numeric_id)
            s.children_offsets.append(ii)

        for lang, term, count in words:
            w = s.words.add()
            w.lang_id = lang
            w.term_id = self.term_id(lang, term)
            w.term_str = term
            w.count = count
            s.raw_count += count

        if len(children) > 0:
            self.internal_synsets_[numeric_id] = s
        else:
            self.leaf_synsets_[numeric_id] = s
        s.hyponym_count = s.raw_count + hyponym_count

        # If we have too many synsets, write them out
        if len(self.leaf_synsets_) >= self.max_leaves_:
            self.FindRoot(self.leaf_synsets_)
            self.Write(self.leaf_wn_)
            self.leaf_wn_ = WordNetFile()
            self.leaf_wn_.root = -1
            self.leaf_synsets_ = {}


def big_test(version="3.0", max_length=3):
    from topicmod.util.wordnet import load_wn
    from nltk.corpus import brown
    from nltk.util import ingrams

    wn = load_wn(version)

    term_counts = defaultdict(int)

    for ngram_length in xrange(max_length):
        token = 0
        for w in ingrams(brown.words(), ngram_length):
            token += 1
            normalized = "_".join(w).lower()
            if wn.synsets(normalized, 'n'):
                term_counts[wn.morphy(normalized)] += 1

    filename = "wn/wordnet.wn"
    if version != "3.0":
        filename = "wn/wordnet_%s.wn" % version
    o = OntologyWriter(filename)
    for ii in orderedTraversal(wn):
        o.AddSynset(ii.offset,
                    ii.name,
                    [x.offset for x in ii.hyponyms() + ii.instance_hyponyms()],
                    [(0, x.name.lower(), term_counts[x.name] + 1)
                     for x in ii.lemmas])
    o.Finalize()


def toy_test():
    """
    Tests the writer on a toy dataset.
    """

    #              ------- (140) 1730 -------
    #           /                              \
    #        (60)              (10)            (80)
    #        1900: ------------2001:---------  2000:
    #          |               gummy_bear (5)    |
    #          |               gummibaer (5)     |
    #          |                                 |
    #          |   animal (5)                    |   food (5)
    #         /\   tier (5)                     /\   essen (5)
    #        /  \                             /    \
    #      (10) (30)                        (40)  (20)
    #      2010 2020                        3000  3010
    #   (5) dog pig (10)               (20) pork  dog (5)
    # (3) hound schwein (20)         (5) schwein  hot dog (5)
    # (2)  hund               (15) schweinfleish  hotdog (5)
    #                                             hotdog (5)
    #  Words (German):
    #  0     essen
    #  1     tier
    #  2     hund
    #  3     schwein
    #  4     schweinfleish
    #  5     hotdog
    #  6     gummibaer
    #  Words (English):
    #  0     food
    #  1     animal
    #  2     dog
    #  3     hound
    #  4     pork
    #  5     pig
    #  6     hot dog
    #  7     hotdog
    #  8     gummy bear

    o = OntologyWriter("wn/animal_food_toy.wn")
    o.AddSynset(1730, "entity", [1900, 2000], [])
    o.AddSynset(1900, "animal", [2010, 2001, 2020],
                [(ENGLISH, "animal", 5), (GERMAN, "tier", 5)])
    o.AddSynset(2000, "food", [3000, 2001, 3010],
                [(ENGLISH, "food", 5), (GERMAN, "essen", 5)])
    o.AddSynset(2001, "gummy bear", [],
                [(ENGLISH, "gummy_bear", 5), (GERMAN, "gumibaer", 5)])
    o.AddSynset(2010, "dog", [],
                [(ENGLISH, "dog", 5), (ENGLISH, "hound", 3),
                 (GERMAN, "hund", 2)])
    o.AddSynset(2020, "pig", [],
                [(ENGLISH, "pig", 10), (GERMAN, "schwein", 20)])
    o.AddSynset(3000, "pork", [],
                [(ENGLISH, "pork", 20), (GERMAN, "schwein", 5),
                 (GERMAN, "schweinfleish", 15)])
    o.AddSynset(3010, "hot dog", [],
                [(ENGLISH, "dog", 5), (ENGLISH, "hot_dog", 5),
                 (GERMAN, "hotdog", 5), (ENGLISH, "hotdog", 5)])
    o.Finalize()


def getLanguage(flag):
    language = {
        '0': ENGLISH,
        '1': GERMAN,
        '2': CHINESE,
        '3': FRENCH,
        '4': SPANISH,
        '5': ARABIC}
    return language[flag]


def getVocab(vocab_fn):
    vocab = set()
    for line in open(vocab_fn):
        words = line.strip()
        w = words.split('\t')
        # check whether we need to use lower case or not
        # vocab.add(w[1].lower())
        # for nih data, we cannot use lower case,
        # since there are words like "pi3" and "PI3"
        vocab.add(w[1])
    return vocab


def readConstraints(filename):
    ml = []
    cl = []
    allcons = list()
    # If no constraint file was specified, give an empty constraint
    if filename:
        infile = open(filename, 'r')
    else:
        infile = []
    for line in infile:
        line = line.strip()
        ww = line.split('\t')
        tmp = map(lambda x: x.lower(), ww[1:])
        if re.search('^SPLIT_', ww[0]):
            cl.append(tmp)
        else: # re.search('^MERGE_', ww[0])
            ml.append(tmp)
    return ml, cl


# generate graph
def generateGraph(constraints, num_flag, graph):
    for cons in constraints:
        for w1 in cons:
            for w2 in cons:
                if w1 != w2:
                    graph[w1][w2] = num_flag
                    graph[w2][w1] = num_flag


def BFS(graph, constraint_words, choice):
    connected_comp = []
    visited_flag = defaultdict()
    for word in constraint_words:
        visited_flag[word] = 0
    for word in constraint_words:
        if visited_flag[word] == 0:
            Q = []
            Q.append(word)
            connected = set()
            while len(Q) > 0:
                node = Q.pop()
                connected.add(node)
                for neighbor in graph[node].keys():
                    if choice == -1:
                        if graph[node][neighbor] > 0:
                            if visited_flag[neighbor] == 0:
                                visited_flag[neighbor] = 1
                                Q.append(neighbor)
                    else:
                        if graph[node][neighbor] == choice:
                            if visited_flag[neighbor] == 0:
                                visited_flag[neighbor] = 1
                                Q.append(neighbor)
            connected_comp.append(connected)
    return connected_comp


# generate a merged link list (connected components in a graph)
def mergeConstraints(constraints):
    # define graph
    graph = defaultdict(dict)
    generateGraph(constraints, 1, graph)

    # get constraint vocab
    constraint_words = set(graph.keys())
    mergedConstr = BFS(graph, constraint_words, -1)

    return mergedConstr


def mergeCL(graph):
    # get constraint vocab
    constraint_words = set(graph.keys())

    # get connected components
    mergedConstr = BFS(graph, constraint_words, -1)

    # merge ml to cl, notice some cl might be merged
    cl_ml_merged = []
    ml_remained = []
    for cl_ml in mergedConstr:
        cl_tmp = BFS(graph, cl_ml, 1)
        ml_tmp = BFS(graph, cl_ml, 2)

        cl_new = []
        for cons in cl_tmp:
            if len(cons) > 1:
                cl_new.append(cons)
        ml_new = []
        for cons in ml_tmp:
            if len(cons) > 1:
                ml_new.append(cons)
        #print cl_new
        #print ml_new

        if len(cl_new) > 0:
            cl_ml_merged.append([cl_new, ml_new])
        else: # len(cl_new) == 0 and len(ml_new) == 1
            assert len(ml_new) == 1
            ml_remained.append(ml_new[0])

    return cl_ml_merged, ml_remained


def flipGraph(cl_list, ml_list, graph):
    """
    for each connected component, generate a flipped graph
    """

    flipped = defaultdict()
    cons_words = reduce(lambda x, y: set(x) | set(y), cl_list)
    if len(ml_list) > 0:
        cons_words |= reduce(lambda x, y: set(x) | set(y), ml_list)

    for ww in cons_words:
        cl_neighbors = set()
        for w in graph[ww]:
            if graph[ww][w] == 1:
                cl_neighbors.add(w)
        #print ww
        #print "cl_neighbors: ", cl_neighbors
        #print "graph: ", graph[ww], "\n\n"
        diff = cons_words.difference(cl_neighbors)
        flipped[ww] = defaultdict()
        # remember after flip, a node has an "edge" connected to itself
        for w in diff:
            flipped[ww][w] = 1
        for w in graph[ww]:
            flipped[ww][w] = 0

    # print "flip: ", flipped

    for ml in ml_list:
        for w1 in ml:
            for w2 in flipped[w1]:
                if flipped[w1][w2] > 0:
                    for w3 in ml:
                        if w1 != w3 and flipped[w3][w2] == 0:
                            flipped[w1][w2] = 0
                            flipped[w2][w1] = 0

            for w2 in ml:
                if w1 != w2:
                    flipped[w1][w2] = 2
                    flipped[w2][w1] = 2

    # print "\n\nflip: ", flipped

    return flipped


def BronKerbosch_v2(R, P, X, G, C):
    """
    find maximum cliques by algorithm BronKerbosch from Wikipedia
    """
    if len(P) == 0 and len(X) == 0:
        if len(R) > 0:
            C.append(sorted(R))
        return

    d = 0
    pivot = ''
    for v in P.union(X):
        neighbors = set()
        for node in G[v]:
            if G[v][node] > 0 and v != node:
                neighbors.add(node)
        if len(neighbors) > d:
            d = len(neighbors)
            pivot = v

    # print d, pivot, P, G[pivot]
    neighbors = set()
    for node in G[v]:
        if G[v][node] > 0 and v != node:
            neighbors.add(node)

    for v in P.difference(neighbors):
        neighbors = set()
        for node in G[v]:
            if G[v][node] > 0 and v != node:
                neighbors.add(node)
        BronKerbosch_v2(R.union(set([v])), P.intersection(neighbors), \
                        X.intersection(neighbors), G, C)
        P.remove(v)
        X.add(v)


def generateCliques(graph):
    cliques = []
    R = set()
    X = set()
    P = set(graph.keys())
    #BronKerbosch(R, P, X, cliques, graph)
    BronKerbosch_v2(R, P, X, graph, cliques)

    return cliques


def generateCannotLinks(cl_ml_merged, graph):
    """
    generate cliques for all cannot links
    """
    cl_updated = []
    for cons in cl_ml_merged:
        cl = cons[0]
        ml = cons[1]
        flipped = flipGraph(cl, ml, graph)
        # print "subgrapph:", len(flipped.keys())
        cliques = generateCliques(flipped)

        # generate cannot links
        new_cons = []
        for clique in cliques:
            clique_remained = clique
            ml_tmp = BFS(flipped, clique, 2)
            ml_new = []
            for cons in ml_tmp:
                if len(cons) > 1:
                    ml_new.append(cons)
                    for word in cons:
                        clique_remained.remove(word)

            if len(ml_new) == 0:
                new_clique = clique_remained
                link_type = "NL_"
            elif len(clique_remained) == 0 and len(ml_new) == 1:
                new_clique = list(ml_new[0])
                link_type = "ML_"
            else:
                new_clique = []
                #link_type = "NL_"
                link_type = "NL_IN_"
                for ml_clique in ml_new:
                    tmp_list = list(ml_clique)
                    new_clique.append(["ML_", tmp_list])
                if len(clique_remained) > 0:
                    new_clique.append(["NL_", clique_remained])
            new_cons.append([link_type, new_clique])

        cl_updated.append(["CL_", new_cons])

    return cl_updated


# generate must links
def generateMustLinks(ml):
    ml_updated = []
    for index in range(len(ml)):
        link_type = "ML_"
        cons = list(ml[index])
        new_cons = [link_type, cons]
        ml_updated.append(new_cons)

    return ml_updated


def mergeAllConstraints(ml, cl):
    # merge ml constrains
    ml_merged = mergeConstraints(ml)

    # generate graph
    graph = defaultdict(dict)
    generateGraph(cl, 1, graph)
    generateGraph(ml_merged, 2, graph)

    # merge links: some ml can be merged into cl, the remained ml will be kept
    # if some cannot links are connected by one must links, they will be merged
    [cl_ml_merged, ml_remained] = mergeCL(graph)
    # print len(cl_ml_merged), len(cl_merged)

    # generate cannot links
    cl_updated = generateCannotLinks(cl_ml_merged, graph)
    # print '\n\n', len(cl_updated)

    # generate must link
    ml_updated = generateMustLinks(ml_remained)
    # print '\n\n', len(ml_updated)

    #for ml in ml_updated:
    #    print "ml: ", ml
    #print '\n\n'

    #for cl in cl_updated:
    #    print "cl: "
    #    for cons in cl[1]:
    #        print cons
    #    print '\n\n'

    # both cl_updated and ml_remained has a link_type
    return ml_updated, cl_updated

def getCliqueCount(cons, constraints_count):
    #if len(cons) == 1:
    #    for word in cons:
    #        if word not in constraints_count.keys():
    #            constraints_count[word] = 1
    #        else:
    #            constraints_count[word] += 1
    #else:
    #    for clique in cons[1]:
    #        constraints_count = getCliqueCount(clique, constraints_count)

    if re.search('ML_', cons[0]) or re.search('NL_', cons[0]) \
           or re.search('CL_', cons[0]):
        for clique in cons[1]:
            constraints_count = getCliqueCount(clique, constraints_count)    
    else:
        print cons
        word = cons
        if word not in constraints_count.keys():
            constraints_count[word] = 1
        else:
            constraints_count[word] += 1

    return constraints_count


def getConstraintCount(ml_updated, cl_updated):
    # both cl_updated and ml_remained has a link_type
    constraints_count = defaultdict()
    for cons in ml_updated:
        constraints_count = getCliqueCount(cons, constraints_count)

    for cons in cl_updated:
        constraints_count = getCliqueCount(cons, constraints_count)

    print "Constraints count: ", constraints_count

    return constraints_count


def write_constraints_test():
    flags.InitFlags()

    # read in vocab
    vocab = getVocab(flags.vocab)
    print "Read vocabulary"

    # read in constraints
    ml_cons, cl_cons = readConstraints(flags.constraints)

    # Merge constraints
    if flags.merge_constraints:
        ml_updated, cl_updated = mergeAllConstraints(ml_cons, cl_cons)
    #else:
    #       ml_updated = map(lambda x: ['ML_', set(x)], ml)
    #       cl_updated = map(lambda x: ['CL_', set(x)], cl)

    # Constraints counts
    constraints_count = getConstraintCount(ml_updated, cl_updated)

    # get constraint vocab
    constraint_words = constraints_count.keys()

    # Check constraints
    check = list(x for x in constraint_words if x not in vocab)
    assert not check, "Constraints were not in vocab: %s" % ", ".join(check)

    # Remained word list
    remained_words = list(x for x in vocab if not (x in constraint_words))



def write_leaf(cons, current_index, leaf_count, allocated_index, o, constraints_count):
    for word in cons:
        current_index += 1
        leaf_count += 1
        wordset = []
        lang = ENGLISH
        if word not in constraints_count.keys():
            num = 1.0
        else:
            num = 1.0 / constraints_count[word]
        wordset.append((lang, word, num))
        leaf_name = "LEAF_%i_%s" % (leaf_count, word)
        o.AddSynset(current_index, leaf_name, [], wordset)

    return current_index, leaf_count, allocated_index


def write_internal_nodes(cons, current_index, leaf_count, allocated_index, o, constraints_count):

    child_count = len(cons[1])

    if child_count == 1:
        [current_index, leaf_count, allocated_index] = \
        write_leaf(cons[1], current_index, leaf_count, allocated_index, o, constraints_count)
        return current_index, leaf_count, allocated_index

    else:

        current_index += 1
        name = cons[0]   
        start = allocated_index + 1
        o.AddSynset(current_index, name, xrange(start, start + child_count), [])
        allocated_index += child_count
        child_index = start - 1

        if not (re.search('^NL_IN_$', cons[0]) or re.search('^CL_$', cons[0])):
            [child_index, leaf_count, allocated_index] = \
            write_leaf(cons[1], child_index, leaf_count, allocated_index, o, constraints_count)
            return current_index, leaf_count, allocated_index

        for clique in cons[1]:
            [child_index, leaf_count, allocated_index] = \
            write_internal_nodes(clique, child_index, leaf_count, allocated_index, o, constraints_count)

    return current_index, leaf_count, allocated_index



def write_leaf_old(cons, current_index, leaf_count, allocated_index, o, constraints_count):

    for word in cons:
        print word
        current_index += 1
        leaf_count += 1
        wordset = []
        lang = ENGLISH
        if word not in constraints_count.keys():
            num = 1.0
        else:
            num = 1.0 / constraints_count[word]
        wordset.append((lang, word, num))
        leaf_name = "LEAF_%i_%s" % (leaf_count, word)
        o.AddSynset(current_index, leaf_name, [], wordset)

    return current_index, leaf_count, allocated_index


def write_internal_nodes_old(cons, current_index, leaf_count, allocated_index, o, constraints_count):

    current_index += 1
    name = cons[0]
    child_count = len(cons[1])
    start = allocated_index + 1
    o.AddSynset(current_index, name, xrange(start, start + child_count), [])
    allocated_index += child_count
    child_index = start - 1

    for clique in cons[1]:
        #if len(clique) == 1:
        #    [child_index, leaf_count, allocated_index] = \
        #    write_leaf(clique, child_index, leaf_count, allocated_index, o, constraints_count)

        #elif len(clique[1]) == 1:
        #    [child_index, leaf_count, allocated_index] = \
        #    write_leaf(clique[1], child_index, leaf_count, allocated_index, o, constraints_count)

        #else:
        #    [child_index, leaf_count, allocated_index] = \
        #    write_internal_nodes(clique[1], child_index, leaf_count, allocated_index, o, constraints_count)

        if re.search('ML_', clique[0]) or re.search('NL_', clique[0]) or re.search('CL_', clique[0]):
            if re.search('ML_', clique[1][0]) or re.search('NL_', clique[1][0]) or re.search('CL_', clique[1][0]):
                [child_index, leaf_count, allocated_index] = \
                write_internal_nodes(clique[1], child_index, leaf_count, allocated_index, o, constraints_count)
            else:
                [child_index, leaf_count, allocated_index] = \
                write_leaf(clique[1], child_index, leaf_count, allocated_index, o, constraints_count)

        else:
            print "clique: ", clique
            [child_index, leaf_count, allocated_index] = \
            write_leaf(clique, child_index, leaf_count, allocated_index, o, constraints_count)

    return current_index, leaf_count, allocated_index


def write_constraints():
    flags.InitFlags()

    # read in vocab
    vocab = getVocab(flags.vocab)
    print "Read vocabulary"

    # read in constraints
    ml_cons, cl_cons = readConstraints(flags.constraints)

    # Merge constraints
    if flags.merge_constraints:
        ml_updated, cl_updated = mergeAllConstraints(ml_cons, cl_cons)
    #else:
    #       ml_updated = map(lambda x: ['ML_', set(x)], ml)
    #       cl_updated = map(lambda x: ['CL_', set(x)], cl)
    print ml_updated
    print cl_updated
	
    # Constraints counts
    constraints_count = getConstraintCount(ml_updated, cl_updated)

    # get constraint vocab
    constraint_words = constraints_count.keys()

    # Check constraints
    check = list(x for x in constraint_words if x not in vocab)
    assert not check, "Constraints were not in vocab: %s" % ", ".join(check)

    # Remained word list
    remained_words = list(x for x in vocab if not (x in constraint_words))

    #########################################################
    print flags.wnname
    wnname = flags.wnname
    o = OntologyWriter(wnname)

    # each ramined word will be seperate node
    num_children = len(ml_updated) + len(cl_updated) + len(remained_words)	

    o.AddSynset(0, "ROOT", xrange(1, num_children + 1), [])
    allocated_index = num_children

    rootchild_index = 0
    leaf_count = 0

    # Add ML constraints
    for cons in ml_updated:
        [rootchild_index, leaf_count, allocated_index] = write_internal_nodes\
        (cons, rootchild_index, leaf_count, allocated_index, o, constraints_count)

    # Add CL constraints
    for cons in cl_updated:
        [rootchild_index, leaf_count, allocated_index] = write_internal_nodes\
        (cons, rootchild_index, leaf_count, allocated_index, o, constraints_count) 

    # Add Unused words
    if len(remained_words) > 0:
        [rootchild_index, leaf_count, allocated_index] = write_leaf\
        (remained_words, rootchild_index, leaf_count, allocated_index, o, constraints_count)

    print ("Added %i total nodes for vocab" % rootchild_index)

    assert rootchild_index == num_children, "Mismatch of children %i %i" \
                   % (rootchild_index, num_children)

    print "Number of leaves:", leaf_count

    # Add root
    o.Finalize()


def write_constraints_old():
    flags.InitFlags()

    # read in vocab
    vocab = getVocab(flags.vocab)
    print "Read vocabulary"

    # read in constraints
    ml_cons, cl_cons = readConstraints(flags.constraints)

    # Merge constraints
    if flags.merge_constraints:
        ml_updated, cl_updated = mergeAllConstraints(ml_cons, cl_cons)
    #else:
    #       ml_updated = map(lambda x: ['ML_', set(x)], ml)
    #       cl_updated = map(lambda x: ['CL_', set(x)], cl)
    print ml_updated
    print cl_updated
	
    # Constraints counts
    constraints_count = getConstraintCount(ml_updated, cl_updated)

    # get constraint vocab
    constraint_words = constraints_count.keys()

    # Check constraints
    check = list(x for x in constraint_words if x not in vocab)
    assert not check, "Constraints were not in vocab: %s" % ", ".join(check)

    # Remained word list
    remained_words = list(x for x in vocab if not (x in constraint_words))

    #########################################################
    print flags.wnname
    wnname = flags.wnname
    o = OntologyWriter(wnname)

    # each ramined word will be seperate node
    num_children = len(ml_updated) + len(cl_updated) + len(remained_words)	

    o.AddSynset(0, "ROOT", xrange(1, num_children + 1), [])
    allocated_index = num_children

    # Add ML constraints
    rcIndex = 0
    ml_count = 0
    leaf_count = 0
    for cons in ml_updated:
        rcIndex += 1
        ml_count += 1
        name = cons[0] + "%i_%s" % (ml_count, ":".join(cons[1])[:20])

        num_leaves = len(cons[1])
        start = allocated_index + 1
        o.AddSynset(rcIndex, name, xrange(start, start + num_leaves), [])
        allocated_index += num_leaves
        lfIndex = start - 1

        for word in cons[1]:
            lfIndex += 1
            leaf_count += 1
            wordset = []
            lang = ENGLISH
            num = 1.0 / constraints_count[word]
            wordset.append((lang, word, num))
            leaf_name = "LEAF_%i_%s" % (leaf_count, word)
            o.AddSynset(lfIndex, leaf_name, [], wordset)

    print ("Added %i ML constraint nodes" % (ml_count))

    # Add CL constraints
    cl_count = 0
    nl_count = 0
    for cons in cl_updated:
        rcIndex += 1
        cl_count += 1

        name = cons[0] + "%i" % (cl_count)
        children_count = len(cons[1])
        start = allocated_index + 1
        o.AddSynset(rcIndex, name, xrange(start, start + children_count), [])
        cl_cIndex = allocated_index
        allocated_index += children_count

        for clique in cons[1]:
            nl_in_child_count = len(clique[1])
            if nl_in_child_count == 1:
                leaf_count += 1
                name = clique[0] + "%i" % (leaf_count)
                cl_cIndex += 1

                assert len(clique[1]) == 1
                word = clique[1][0]
                lang = ENGLISH
                num = 1.0 / constraints_count[word]
                wordset = []
                wordset.append((lang, word, num))
                leaf_name = "LEAF_%i_%s" % (leaf_count, word)
                o.AddSynset(cl_cIndex, leaf_name, [], wordset)
        
            elif re.search('^NL_IN_$', clique[0]): # four levels tree
                nl_count += 1
                name = clique[0] + "%i" % (nl_count)
                nl_in_child_count = len(clique[1])
                cl_cIndex += 1
                start = allocated_index + 1
                o.AddSynset(cl_cIndex, name, \
                            xrange(start, start + nl_in_child_count), [])
                nl_in_cIndex = allocated_index
                allocated_index += nl_in_child_count

                for in_clique in clique[1]:


                    if re.search('^ML_$', in_clique[0]):
                        ml_count += 1
                        name = in_clique[0] + "%i_%s" % \
                               (ml_count, ":".join(in_clique[1])[:20])

                        nl_in_cIndex += 1
                        num_leaves = len(in_clique[1])
                        start = allocated_index + 1
                        o.AddSynset(nl_in_cIndex, name, xrange(start, start + num_leaves), [])
                        allocated_index += num_leaves

                        lfIndex = start - 1
                        for word in in_clique[1]:
                            lfIndex += 1
                            leaf_count += 1
                            wordset = []
                            lang = ENGLISH
                            num = 1.0 / constraints_count[word]
                            wordset.append((lang, word, num))
                            leaf_name = "LEAF_%i_%s" % (leaf_count, word)
                            o.AddSynset(lfIndex, leaf_name, [], wordset)

                    else: # re.search('^NL_$', clique[0]):
                        leaf_count += 1
                        name = in_clique[0] + "%i_%s" % \
                               (leaf_count, ":".join(in_clique[1])[:20])

                        wordset = []
                        for word in in_clique[1]:
                            lang = ENGLISH
                            num = 1.0 / constraints_count[word]
                            wordset.append((lang, word, num))

                        assert len(wordset) == 1

                        nl_in_cIndex += 1
                        o.AddSynset(nl_in_cIndex, name, [], wordset)

            else: # three levels tree
                if re.search('^ML_$', clique[0]):
                    ml_count += 1
                    name = clique[0] + "%i_%s" % \
                           (ml_count, ":".join(clique[1])[:20])
                else: # re.search('^NL_$', clique[0]):
                    nl_count += 1
                    name = clique[0] + "%i_%s" % \
                           (nl_count, ":".join(clique[1])[:20])

                cl_cIndex += 1
                num_leaves = len(clique[1])
                start = allocated_index + 1
                o.AddSynset(cl_cIndex, name, xrange(start, start + num_leaves), [])
                allocated_index += num_leaves
                lfIndex = start - 1
                for word in clique[1]:
                    lfIndex += 1
                    leaf_count += 1
                    wordset = []
                    lang = ENGLISH
                    num = 1.0 / constraints_count[word]
                    wordset.append((lang, word, num))
                    leaf_name = "LEAF_%i_%s" % (leaf_count, word)
                    o.AddSynset(lfIndex, leaf_name, [], wordset)

    # Unused words
    if len(remained_words) > 0:
        for word in remained_words:
            rcIndex += 1
            leaf_count += 1
            wordset = []
            lang = ENGLISH
            num = 1.0
            wordset.append((lang, word, num))
            leaf_name = "LEAF_%i_%s" % (leaf_count, word)
            o.AddSynset(rcIndex, leaf_name, [], wordset)

    print ("Added %i total nodes for vocab" % rcIndex)

    assert rcIndex == num_children, "Mismatch of children %i %i" \
                   % (rcIndex, num_children)

    # Add root
    o.Finalize()


if __name__ == "__main__":
    flags.InitFlags()

    # write_constraints_test()

    if flags.write_constraints:
        write_constraints()

    if flags.write_toy:
        toy_test()

    if flags.write_wordnet:
        big_test()
