package com.neoshell.nlp.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.neoshell.nlp.core.NLPContext;
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

public class NLPUtilClient {

  private final ManagedChannel channel;
  private final NLPUtilGrpc.NLPUtilBlockingStub nlpUtilBlockingStub;
  private final MessageAnalysisUtilGrpc.MessageAnalysisUtilBlockingStub messageAnalysisUtilBlockingStub;

  public NLPUtilClient(String host, int port) {
    this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true)
        .build());
  }

  NLPUtilClient(ManagedChannel channel) {
    this.channel = channel;
    nlpUtilBlockingStub = NLPUtilGrpc.newBlockingStub(channel);
    messageAnalysisUtilBlockingStub = MessageAnalysisUtilGrpc
        .newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public NLPContext generateNLPContext(Map<String, Long> globalWordCount)
      throws StatusRuntimeException {
    GenerateNLPContextRequest request = GenerateNLPContextRequest.newBuilder()
        .putAllGlobalWordCount(globalWordCount).build();
    GenerateNLPContextReply reply = nlpUtilBlockingStub
        .generateNLPContext(request);
    return reply.getNlpContext();
  }

  public List<String> segment(List<String> textList)
      throws StatusRuntimeException {
    SegmentRequest request = SegmentRequest.newBuilder().addAllText(textList)
        .build();
    SegmentReply reply = nlpUtilBlockingStub.segment(request);
    return reply.getWordList();
  }

  public boolean isStopWord(String word) throws StatusRuntimeException {
    IsStopWordRequest request = IsStopWordRequest.newBuilder().setWord(word)
        .build();
    IsStopWordReply reply = nlpUtilBlockingStub.isStopWord(request);
    return reply.getIsStopWord();
  }

  public List<String> removeStopWords(List<String> words)
      throws StatusRuntimeException {
    RemoveStopWordsRequest request = RemoveStopWordsRequest.newBuilder()
        .addAllWord(words).build();
    RemoveStopWordsReply reply = nlpUtilBlockingStub.removeStopWords(request);
    return reply.getWordList();
  }

  public List<WordInfo> countWords(List<String> texts, boolean countStopWords,
      int limit) {
    CountWordsRequest request = CountWordsRequest.newBuilder().addAllText(texts)
        .setCountStopWords(countStopWords).setLimit(limit).build();
    CountWordsReply reply = nlpUtilBlockingStub.countWords(request);
    return reply.getWordCountList();
  }

  public List<WordInfo> getKeywordInfo(List<String> words, NLPContext context,
      int limit) throws StatusRuntimeException {
    GetKeywordInfoRequest request = GetKeywordInfoRequest.newBuilder()
        .addAllWord(words).setNlpContext(context).setLimit(limit).build();
    GetKeywordInfoReply reply = nlpUtilBlockingStub.getKeywordInfo(request);
    return reply.getKeywordInfoList();
  }

  public List<Conversation> mergeMessagesAndComputeKeywords(
      List<Message> messages, MessageAnalysisContext context)
          throws StatusRuntimeException {
    MergeMessagesAndComputeKeywordsRequest request = MergeMessagesAndComputeKeywordsRequest
        .newBuilder().addAllMessage(messages).setContext(context).build();
    MergeMessagesAndComputeKeywordsReply reply = messageAnalysisUtilBlockingStub
        .mergeMessagesAndComputeKeywords(request);
    return reply.getConversationList();
  }

}
