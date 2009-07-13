////////////////Groovy callback event/////////////////

public class TropoChoice {
  def concept,interpretation,confidence,xml,utterance
  TropoChoice(concept=null,interpretation=null,confidence=null,xml=null,utterance=null){
    this.concept=concept;
    this.interpretation=interpretation;
    this.confidence=confidence;
    this.xml=xml;
    this.utterance=utterance;
  }
}

public class TropoEvent {
  def name
  def value
  def recordURI
  def choice
  
  TropoEvent(name,value, recordURI=null, choice=null) {
    this.name = name
    this.value = value
    this.recordURI = recordURI
    this.choice=choice
  }
  
  void onChoice(expected, callback) {
    if (this.name == "choice") {
      if (this.value == expected) {
        _handleCallBack(callback)
      }
    }
  }
  void onBadChoice(callback) {
    if (this.name == "choice") {
      if (this.value == "nomatch") {
        _handleCallBack(callback)
      }
    }
  }
  
  void onTimeout(callback) {
    if (this.name == "timeout") {
      _handleCallBack(callback)
    }
  } 
  
  void onError(callback) {
    if (this.name == "error") {
      _handleCallBack(callback)
    }
  } 
  
  void onHangup(callback) {
    if (this.name == "hangup") {
      _handleCallBack(callback)
    }
  } 

  void onRecord(callback){
    if(this.recordURI!=null && this.recordURI!=""){
      _handleCallBack(callback,this)
    }
  }

  void onSilenceTimeout(callback){
    if(this.name == "silenceTimeout"){
      _handleCallBack(callback)
    }
  }

  public static int _parseTime(def time){ 
    time = time.toString().toLowerCase()
    if(time.endsWith("s")){
      time=time.substring(0,time.length()-1)
    }
    time = time.toFloat()*1000
    time = time.toInteger()
    time = Math.max(0, time) //convert float second to int ms
    return time
  }

  public static void _handleCallBack (cb, ev=null){
    try{
      if(ev!=null){
        cb(ev)
      }
      else{
        cb()
      }
    } catch (e){
      //log(" ---- Callback Error : " + e.message)
    }
  }
}

//////////////////////// tropoCall && APP ///////////////////////
public class TropoApp {
  def _app, baseDir
  TropoApp(app) {
    this._app = app
    this.baseDir = app.getApp().getBaseDir()
  }
}
public class TropoCall {
  
  def _call, calledID, callerID, callerName, calledName
  
  TropoCall(_call){
    this._call = _call
    this.callerID=_call.getCallerId()
    this.calledID=_call.getCalledId()
    this.callerName= _call.getCallerName()
    this.calledName= _call.getCalledName()
  }
  
  def log (msg) {
    _call.log(msg)
  }
  
  def getHeader(name) {
    return _call.getHeader(name)
  }
  
  def await(milliSeconds=0) {
    _call.block(milliSeconds)
  }

  def state() {
     return _call.getState().toString()
  }
  
  def isActive() {
     return _call.isActive()
  }
  
  def redirect(too) {
    _call.redirect(too)
  }
  
  def answer (timeout=30) { // in second
    _call.answer(timeout*1000)
  }
  
  def startCallRecording (uri, format='audio/wav', key='', keyUri='') {
    _call.startCallRecording(uri, format, key, keyUri)
  }
  
  def stopCallRecording () {
    _call.stopCallRecording()
  }
  
  def reject () {
    _call.reject()
  }
  
  def hangup () {
    return _call.hangup()
  }
  
  def say (tts) {
    return prompt(tts)
  }
  
  def ask (tts, options=null) { 
    return prompt(tts, options)
  }

  def record(tts, options=null) { 
    def oop = options
    if(oop!=null){ 
      oop['record'] = true
    }
    else{
      oop = [repeat:1,record:true, beep:true, silenceTimeout:3, maxTime:30,timeout:30]
    }
    return prompt(tts,oop)
  }
  
  def transfer(too, options=null){
    def answerOnMedia = false
    def callerID = null
    def timeout = 30000
    def method = "bridged"
    def playrepeat = 1
    def playvalue = null
    def choices=null 
    
    def onSuccess=null
    def onError=null
    def onTimeout=null
    def onCallFailure=null
    def onChoice=null
    
    if(options){ 
      if(options["answerOnMedia"] != null) {
        answerOnMedia = options["answerOnMedia"]
      }
      if(options["callerId"] != null) {
        callerID = options["callerId"]
      }
      if(options["callerID"] != null) {
        callerID = options["callerID"]
      }
      if(options["timeout"] != null) {
        timeout = TropoEvent._parseTime(options["timeout"]);
      }
      method = options["method"]
      if(options["playrepeat"]!=null){
        playrepeat = options["playrepeat"] 
      }
      if(options["playvalue"]!=null){
        playvalue = options["playvalue"] 
      }
      choices = options["choices"]
      
      onSuccess = options["onSuccess"]
      onError = options["onError"]
      onTimeout = options["onTimeout"]
      onCallFailure = options["onCallFailure"]
      onChoice = options["onChoice"]
    }
    def event  = null
    
    try{
      def _call_ = new TropoCall(_call.transfer(too, callerID, answerOnMedia,timeout, playvalue, playrepeat, choices))
      event = new TropoEvent("transfer", _call_)
      if(onSuccess != null){
        TropoEvent._handleCallBack(onSuccess,event)
      }
    } catch (e){
      if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="Outbound call is timeout.")){
        event = new TropoEvent("timeout", null) // create event based on the timeout
        if(onTimeout != null){
          TropoEvent._handleCallBack(onTimeout,event)
        }
      }
      else if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="Outbound call can not complete.")){
        event = new TropoEvent("callfailure", null)
        if (onCallFailure != null) {
          TropoEvent._handleCallBack(onCallFailure,event)
        }
      }
      else if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="Outbound call cancelled.")){
        event = new TropoEvent("choice", null)
        if (onChoice != null) {
          TropoEvent._handleCallBack(onChoice,event)
        }
      }
      else {
        log(e)
        event = new TropoEvent("error", e.message)
        //TODO: ??? do we need to tell the callback what kind of error it is???
        if(onError != null){
          TropoEvent._handleCallBack(onError)
        }
        throw e
      }
    }
    return event
  }

  //if choice and record are on, return event.name = choice and event.value = grammar result and event.recordUI = recordURI
  //if choice is on but not record, return event.name = choice, event.value = grammar result, and event.recordURI = null
  //if record is on but not choice, return event.name = record, event.value = recordURI, and event.recordURI = recordURI
  def prompt(tts, options=null){
    def grammar = ""
    def timeout = 30000  //milliseconds
    def onTimeout = null
    def onChoices = null
    def repeat = 0
    def onError = null
    def onEvent = null
    def onHangup = null
    def onBadChoice = null
    def ttsOrUrl = ""
    def bargein = true
    def choiceConfidence='0.3'
    def choiceMode='any' // (dtmf|speech|any) - defaults to any
    
    //record parameters
    def record = false
    def beep = true
    def silenceTimeout = 5000  // 5 seconds
    def maxTime = 30000 // 30 seconds

    def onSilenceTimeout = null
    def onRecord = null

    if(tts != null){//make sure the ttsOrUrl is at least an empty string before calling IncomingCall
      ttsOrUrl = tts
    }
    //println ttsOrUrl
    
    if (options != null) {
      if(options["choices"]){ //make sure the grammar is at least an empty string before calling IncomingCall
        grammar = options["choices"] 
      }
      onChoices = options["onChoice"]
      if(options["timeout"] != null) {
        timeout = TropoEvent._parseTime(options["timeout"]);
      }
      onTimeout = options["onTimeout"]
      if(options["repeat"] != null){
        repeat = Math.max(0, options["repeat"].toInteger())
      }
      onError = options["onError"]
      onEvent = options["onEvent"]
      onHangup = options["onHangup"]
      onBadChoice = options["onBadChoice"]
      if(options["bargein"] != null){
        bargein = options["bargein"]; 
      }
      if(options["choiceConfidence"] != null){
        choiceConfidence = options["choiceConfidence"].toString(); 
      }
      if(options["choiceMode"] != null){
        choiceMode = options["choiceMode"]; 
      }
      //record
      if(options["record"] != null){
        record = options["record"]; 
      }
      if(options["beep"] != null){
        beep = options["beep"]; 
      }
      if(options["silenceTimeout"] != null){
        silenceTimeout = TropoEvent._parseTime(options["silenceTimeout"]);
      }
      if(options["maxTime"] != null){
        maxTime = TropoEvent._parseTime(options["maxTime"]);
      }
      onSilenceTimeout = options["onSilenceTimeout"];
      onRecord = options["onRecord"];
    }
    
    def event = null
    
    for (def x=0; x<=repeat; x++){
      try{
        if(record){
          def result = _call.promptWithRecord(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout, record, beep, maxTime, silenceTimeout )
          event = new TropoEvent("record", result.get('recordURL'), result.get('recordURL'));
          if (onRecord != null) {
            TropoEvent._handleCallBack(onRecord,event);
          }
          if( grammar != null && grammar != '' ){ // both choice and record are enabled
            def choice = new TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
            event = new TropoEvent("choice", result.get('value'), result.get('recordURL'), choice);
            if (onChoices != null) {
              TropoEvent._handleCallBack(onChoices,event);
            }
          }
        }
        else{
          def result = _call.prompt(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout)
          def choice = new TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
          event = new TropoEvent("choice", result.get('value'), result.get('recordURL'), choice);
          if (onChoices != null) {
            TropoEvent._handleCallBack(onChoices,event)
          }
        }
        if (onEvent != null) {
          TropoEvent._handleCallBack(onEvent,event)
        }
        break
      } catch (e){
        //println  ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"+e.message
        if( ((e instanceof com.voxeo.tropo.ErrorException) && (e.message=="NO_SPEECH")) || (e.message=="java.lang.RuntimeException: NoSpeech")){
          event = new TropoEvent("timeout", null) // create event based on the timeout
          if(onTimeout != null){
            TropoEvent._handleCallBack(onTimeout,event)
          }
          if (onEvent != null) {
            TropoEvent._handleCallBack(onEvent,event)
          }
        }
        else if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="NO_MATCH")){
          event = new TropoEvent("badChoice", null)
          if (onBadChoice != null) {
            TropoEvent._handleCallBack(onBadChoice,event)
          }
          if (onEvent != null) {
            TropoEvent._handleCallBack(onEvent,event)
          }
        }
        else if( e.message=="com.mot.mrcp.MrcpDisconnectedException: Disconnect"){
          event = new TropoEvent("hangup", null) 
          if(onHangup != null){
            TropoEvent._handleCallBack(onHangup,event)
          }
          if (onEvent != null) {
            TropoEvent._handleCallBack(onEvent,event)
          }
          break
        }
        else if( e.message=="java.lang.RuntimeException: silenceTimeout"){
          event = new TropoEvent("silenceTimeout", null) 
          if(onSilenceTimeout != null){
            TropoEvent._handleCallBack(onSilenceTimeout)
          }
          if (onEvent != null) {
            TropoEvent._handleCallBack(onEvent,event)
          }
        }
        else {
          log(e)
          event = new TropoEvent("error", e.message)
          //TODO: ??? do we need to tell the callback what kind of error it is???
          if(onError != null){
            TropoEvent._handleCallBack(onError)
          }
          if (onEvent != null) {
            TropoEvent._handleCallBack(onEvent,event)
          }
         throw e  //stop the action on error
        }
      }
    }
    return event
  }
}

/////////////////// Global functions begin ///////////////////

currentApp = new TropoApp(appInstance) //global app object, instance of TropoApp
 
currentCall = null // global call object, instance of TropoCall
if (incomingCall != "nullCall"){
  currentCall = new TropoCall(incomingCall)
  currentCall.log("currentCall is assigned to incoming " + incomingCall.toString())
}

def call(too, options=null){
  def callerID = 'sip:Tropo@10.6.69.201'
  def answerOnMedia = false
  def timeout = 30000
  
  def onAnswer = null
  def onError = null
  def onTimeout = null
  def onCallFailure = null
  
  def recordURI = ''
  def recordFormat='audio/wav'
  
  if (options) {
    onAnswer = options['onAnswer']
    onError = options['onError']
    onTimeout = options['onTimeout']
    onCallFailure = options['onCallFailure']
    
    if(options["timeout"] != null) {
      timeout = TropoEvent._parseTime(options["timeout"]);
    }
    
    if(options['answerOnMedia'] != null){
      answerOnMedia = options['answerOnMedia']
    } 
    if(options['callerID'] != null){
      callerID = options['callerID']
    } 
    if(options['callerId'] != null){
      callerID = options['callerId']
    } 
    if(options['recordURI'] != null){
      recordURI = options['recordURI']
    } 
    if(options['recordFormat'] != null){
      recordFormat = options['recordFormat']
    } 
  }
  
  def event  = null
  
  try{
    def _newCall_=callFactory.call(callerID, too, answerOnMedia, timeout, recordURI, recordFormat)
    def _call_ = new TropoCall(_newCall_)
    if ( currentCall == null ){
      currentCall =  _call_ 
      currentCall.log("currentCall is assigned to outgoing " + _newCall_.toString())
    }
    event = new TropoEvent("answer", _call_)
    if(onAnswer != null){
      TropoEvent._handleCallBack(onAnswer,event)
    }
  } catch (e){
    if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="Outbound call is timeout.")){
      event = new TropoEvent("timeout", null) // create event based on the timeout
      if(onTimeout != null){
        TropoEvent._handleCallBack(onTimeout,event)
      }
    }
    else if( (e instanceof com.voxeo.tropo.ErrorException) && (e.message=="Outbound call can not complete.")){
      event = new TropoEvent("callfailure", null)
      if (onCallFailure != null) {
        TropoEvent._handleCallBack(onCallFailure,event)
      }
    }
    else {
      log(e)
      event = new TropoEvent("error", e.message)
      //TODO: ??? do we need to tell the callback what kind of error it is???
      if(onError != null){
        TropoEvent._handleCallBack(onError)
      }
      throw e
    }
  }
  return event
}

def log(msg=null) {
  if(currentCall!=null && currentCall.isActive()){
    currentCall.log(msg);
  }
  else {
    appInstance.log(msg);
  }
}

def await(milliSeconds=0) {
  if(currentCall!=null && currentCall.isActive()){
    currentCall.await(milliSeconds);
  }
  else {
    appInstance.block(milliSeconds);
  }
}

def redirect(too) {
  currentCall.redirect(too);
}

def answer(timeout=30) { // in second
  if(currentCall != null){
    currentCall.answer(timeout);
  }
}

def startCallRecording (uri, format='audio/wav', key='', keyUri='') {
  if(currentCall != null){
    currentCall.startCallRecording(uri, format, key,keyUri);
  }
}

def stopCallRecording () {
  if(currentCall != null){
    currentCall.stopCallRecording();
  }
}

def reject() {
  currentCall.reject();
}

def hangup() {
  return currentCall.hangup();
}

def say(tts) {
  return currentCall.say(tts);
}

def ask(tts, options) {
  return currentCall.ask(tts, options);
}

def transfer(tts, options=null){
  return currentCall.transfer(tts, options);
}

def prompt(tts, options=null){
  return currentCall.prompt(tts, options);
}

def record(tts, options=null){
  return currentCall.record(tts, options);
}

////////////////////end shim of Groovy//////////////////
