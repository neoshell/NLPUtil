NLPUtil
===================================

This is a Maven project which provides basic NLP functions.<br>
Tested with English, Chinese. Not sure if it works with other languages.<br>

You can use it in 2 ways:
1. Use as a library. The classes under <b>com.neoshell.nlp.core</b> and <b>com.neoshell.nlp.messaging</b> contain all the core business logics.
2. It also implements a server and a client using [gRPC](https://grpc.io/). You can run it as a service.

### Functionality

Basic functions:
1. Word segmentation.
2. Checking stop words.
3. Removing stop words.
4. Counting words.
5. Computing keywords based on frequency.

Messaging analysis:
1. Clustering related messages and computing keywords.

See <b>nlp_service.proto</b> for more details.

### How to use

1. This project has dependency on [Stanford Word Segmenter](https://nlp.stanford.edu/software/segmenter.shtml). You need to download (from the link) and extract the software (compatibility tested with 3.7.0).
2. Put the Stanford Word Segmenter jar file into the lib folder (or modify the path in <b>pom.xml</b> if you like).
3. Edit <b>config.ini</b>, set Stanford Word Segmenter directory and stop word files.
4. Build jar file and use it.
