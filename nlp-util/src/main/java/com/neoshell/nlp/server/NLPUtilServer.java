package com.neoshell.nlp.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;

import org.ini4j.Wini;

import com.neoshell.nlp.core.NLPContext;
import com.neoshell.nlp.core.NLPUtil;
import com.neoshell.nlp.core.WordInfo;
import com.neoshell.nlp.grpc.CountWordsReply;
import com.neoshell.nlp.grpc.CountWordsRequest;
import com.neoshell.nlp.grpc.GenerateNLPContextReply;
import com.neoshell.nlp.grpc.GenerateNLPContextRequest;
import com.neoshell.nlp.grpc.GetKeywordInfoReply;
import com.neoshell.nlp.grpc.GetKeywordInfoRequest;
import com.neoshell.nlp.grpc.IsStopWordReply;
import com.neoshell.nlp.grpc.IsStopWordRequest;
import com.neoshell.nlp.grpc.MergeMessagesAndComputeKeywordsReply;
import com.neoshell.nlp.grpc.MergeMessagesAndComputeKeywordsRequest;
import com.neoshell.nlp.grpc.MessageAnalysisUtilGrpc;
import com.neoshell.nlp.grpc.NLPUtilGrpc;
import com.neoshell.nlp.grpc.RemoveStopWordsReply;
import com.neoshell.nlp.grpc.RemoveStopWordsRequest;
import com.neoshell.nlp.grpc.SegmentReply;
import com.neoshell.nlp.grpc.SegmentRequest;
import com.neoshell.nlp.messaging.Conversation;
import com.neoshell.nlp.messaging.Message;
import com.neoshell.nlp.messaging.MessageAnalysisContext;
import com.neoshell.nlp.messaging.MessageAnalysisUtil;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class NLPUtilServer {

  private static final String LOG_FILE_NAME_PATTERN = "server_%g.log";
  private static Logger logger;

  private NLPUtil nlpUtil;
  private MessageAnalysisUtil messageAnalysisUtil;
  private int port;
  private Server server;

  public static Logger getLogger() {
    return logger;
  }

  private void start(String configFile) throws IOException {
    loadConfig(configFile);
    server = ServerBuilder.forPort(port).addService(new NLPUtilImpl(nlpUtil))
        .addService(new MessageAnalysisUtilImpl(messageAnalysisUtil)).build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM
        // shutdown hook.
        System.err
            .println("Shutting down gRPC server since JVM is shutting down");
        NLPUtilServer.this.stop();
        System.err.println("Server shut down");
      }
    });
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private void loadConfig(String configFile) throws IOException {
    Wini config = new Wini(new File(configFile));
    port = config.get("Server", "Port", int.class);
    Properties props = new Properties();
    String segmenterDir = config.get("NLP", "StanfordSegmenterDir",
        String.class);
    props.setProperty("sighanCorporaDict", segmenterDir);
    props.setProperty("serDictionary", segmenterDir + "/dict-chris6.ser.gz");
    props.setProperty("inputEncoding", "UTF-8");
    props.setProperty("sighanPostProcessing", "true");
    CRFClassifier<CoreLabel> segmenter = new CRFClassifier<>(props);
    segmenter.loadClassifierNoExceptions(segmenterDir + "/ctb.gz", props);

    String stopWordsEnglishFilePath = config.get("NLP", "StopWordsEnglish",
        String.class);
    List<String> stopWordsEnglish = readTextLines(stopWordsEnglishFilePath);
    logger.info("Loaded English stop words from " + stopWordsEnglishFilePath);
    String stopWordsChineseFilePath = config.get("NLP", "StopWordsChinese",
        String.class);
    List<String> stopWordsChinese = readTextLines(stopWordsChineseFilePath);
    logger.info("Loaded Chinese stop words from " + stopWordsChineseFilePath);

    nlpUtil = new NLPUtil(segmenter);
    nlpUtil.addStopwords(stopWordsEnglish);
    nlpUtil.addStopwords(stopWordsChinese);
    messageAnalysisUtil = new MessageAnalysisUtil(nlpUtil);
  }

  private List<String> readTextLines(String filePath) throws IOException {
    List<String> lines = new ArrayList<>();
    BufferedReader br = new BufferedReader(
        new InputStreamReader(new FileInputStream(filePath)));
    String line = null;
    while ((line = br.readLine()) != null) {
      lines.add(line);
    }
    br.close();
    return lines;
  }

  public static void main(String[] args) {
    try {
      logger = CustomizedLogger.getLogger(NLPUtilServer.class,
          LOG_FILE_NAME_PATTERN);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Failed to create logger.");
      return;
    }
    try {
      String configFilePath = args.length >= 1 ? args[0] : "config.ini";
      final NLPUtilServer server = new NLPUtilServer();
      server.start(configFilePath);
      server.blockUntilShutdown();
    } catch (Exception e) {
      logger.severe(ExceptionUtils.getStackTrace(e));
    }
  }

  static class NLPUtilImpl extends NLPUtilGrpc.NLPUtilImplBase {

    private NLPUtil nlpUtil;

    public NLPUtilImpl(NLPUtil nlpUtil) {
      this.nlpUtil = nlpUtil;
    }

    @Override
    public void generateNLPContext(GenerateNLPContextRequest req,
        StreamObserver<GenerateNLPContextReply> responseObserver) {
      Map<String, Long> globalWordCount = req.getGlobalWordCountMap();
      NLPContext nlpContext = nlpUtil.generateNLPContext(globalWordCount);
      GenerateNLPContextReply reply = GenerateNLPContextReply.newBuilder()
          .setNlpContext(nlpContext).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void segment(SegmentRequest req,
        StreamObserver<SegmentReply> responseObserver) {
      List<String> texts = req.getTextList();
      List<String> words = nlpUtil.segment(texts);
      SegmentReply reply = SegmentReply.newBuilder().addAllWord(words).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void isStopWord(IsStopWordRequest req,
        StreamObserver<IsStopWordReply> responseObserver) {
      String word = req.getWord();
      boolean isStopWord = nlpUtil.isStopWord(word);
      IsStopWordReply reply = IsStopWordReply.newBuilder()
          .setIsStopWord(isStopWord).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void removeStopWords(RemoveStopWordsRequest req,
        StreamObserver<RemoveStopWordsReply> responseObserver) {
      List<String> words = req.getWordList();
      words = nlpUtil.removeStopWords(words);
      RemoveStopWordsReply reply = RemoveStopWordsReply.newBuilder()
          .addAllWord(words).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void countWords(CountWordsRequest req,
        StreamObserver<CountWordsReply> responseObserver) {
      List<String> texts = req.getTextList();
      boolean countStopWords = req.getCountStopWords();
      int limit = req.getLimit();
      List<WordInfo> wordCountList = nlpUtil.countWords(texts, countStopWords,
          limit);
      CountWordsReply reply = CountWordsReply.newBuilder()
          .addAllWordCount(wordCountList).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getKeywordInfo(GetKeywordInfoRequest req,
        StreamObserver<GetKeywordInfoReply> responseObserver) {
      List<String> words = req.getWordList();
      NLPContext context = req.getNlpContext();
      int limit = req.getLimit();
      List<WordInfo> keywordInfo = nlpUtil.getKeywordInfo(words, context,
          limit);
      GetKeywordInfoReply reply = GetKeywordInfoReply.newBuilder()
          .addAllKeywordInfo(keywordInfo).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

  }

  static class MessageAnalysisUtilImpl
      extends MessageAnalysisUtilGrpc.MessageAnalysisUtilImplBase {

    private MessageAnalysisUtil messageAnalysisUtil;

    public MessageAnalysisUtilImpl(MessageAnalysisUtil messageAnalysisUtil) {
      this.messageAnalysisUtil = messageAnalysisUtil;
    }

    @Override
    public void mergeMessagesAndComputeKeywords(
        MergeMessagesAndComputeKeywordsRequest req,
        StreamObserver<MergeMessagesAndComputeKeywordsReply> responseObserver) {
      List<Message> messages = req.getMessageList();
      MessageAnalysisContext context = req.getContext();
      List<Conversation> conversations = messageAnalysisUtil
          .mergeMessagesAndComputeKeywords(messages, context);
      MergeMessagesAndComputeKeywordsReply reply = MergeMessagesAndComputeKeywordsReply
          .newBuilder().addAllConversation(conversations).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

  }

}
