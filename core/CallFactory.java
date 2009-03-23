package com.voxeo.tropo.core;

import java.io.IOException;

public interface CallFactory {

  OutgoingCall call(final String from, final String to, final boolean answerOnMedia, final long timeout) throws IOException;
}
