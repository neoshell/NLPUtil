package com.neoshell.nlp.test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import com.neoshell.nlp.core.WordInfo;
import com.neoshell.nlp.messaging.Message;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class TestUtil {

  private static final String CONFIG_FILE_PATH = "config.ini";

  public static CRFClassifier<CoreLabel> createSegmenter()
      throws InvalidFileFormatException, IOException {
    Wini config = new Wini(new File(CONFIG_FILE_PATH));
    String segmenterDir = config.get("NLP", "StanfordSegmenterDir",
        String.class);
    Properties props = new Properties();
    props.setProperty("sighanCorporaDict", segmenterDir);
    props.setProperty("serDictionary", segmenterDir + "/dict-chris6.ser.gz");
    props.setProperty("inputEncoding", "UTF-8");
    props.setProperty("sighanPostProcessing", "true");
    CRFClassifier<CoreLabel> segmenter = new CRFClassifier<>(props);
    segmenter.loadClassifierNoExceptions(segmenterDir + "/ctb.gz", props);
    return segmenter;
  }

  public static WordInfo createWordInfo(String word, long count, double score) {
    return WordInfo.newBuilder().setWord(word).setCount(count).setScore(score)
        .build();
  }

  public static Message createMessage(long id, long timestampSeconds,
      String fromUserId, String toUserId, String content) {
    return Message.newBuilder().setId(id).setTimestampSeconds(timestampSeconds)
        .setFromUserId(fromUserId).setToUserId(toUserId).setContent(content)
        .build();
  }

}
