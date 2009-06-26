package com.voxeo.tropo.core;

import java.io.IOException;

public interface CallFactory {

  /**
   * 
   * @param from
   * @param to
   * @param answerOnMedia
   * @param timeout
   * @param callRecordUri when it is not empty, this call will be recored and saved to a file indicated by callRecordUri.
   * @param callRecordFormat one of audio/wav, audio/gsm, audio/au
   * @return
   * @throws IOException
   */
  OutgoingCall call(final String from, final String to, final boolean answerOnMedia, final int timeout, final String callRecordUri, final String callRecordFormat) throws IOException;
}
