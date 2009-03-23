from java.lang import RuntimeException
from java.lang import String
#######################################callback event
def _parseTime(time):
    if time is str:
       time = time.lower()
       time = time.replace('s','') 
    time = int(float(time)*1000) # #convert float second to int ms
    if time<0 :
       time=0 
    return time

def _handleCallBack(cb, ev=None):
  try:
    if(ev!=None):
      cb(ev)
    else:
      cb()
  except RuntimeException,e:
    log(" ---- Callback Error : " + e.message)

class TropoChoice:
  def __init__(self, concept=None,interpretation=None,confidence=None,xml=None,utterance=None):
    self.concept=concept
    self.interpretation=interpretation
    self.confidence=confidence
    self.xml=xml
    self.utterance=utterance


class TropoEvent:
  def __init__(self, name, value, recordURI=None,choice=None):
    self.name=name
    self.value=value
    self.recordURI=recordURI
    self.choice=choice

  def onChoice(self, expected, callback): 
    if self.name == "choice" and self.value == expected:
       _handleCallBack(callback)
    return True

  def onBadChoice(self, callback): 
    if self.name == "choice" and self.value == "nomatch":
       _handleCallBack(callback)
    return True

  def onTimeout(self, callback): 
    if self.name == "timeout":
       _handleCallBack(callback)
    return True

  def onError(self, callback): 
    if self.name == "error":
       _handleCallBack(callback)
    return True

  def onHangup(self, callback): 
    if self.name == "hangup":
       _handleCallBack(callback)
    return True

  def onRecord(self, callback):
    if self.recordURI!=None and self.recordURI!="":
       _handleCallBack(callback,self)

  def onSilenceTimeout(self, callback):
    if self.name =="silenceTimeout":
       _handleCallBack(callback)
        
#####################################tropoCall 

class TropoApp :
  def __init__(self, _app):
    self._app = _app
    self.baseDir=_app.getApp().getBaseDir()

class TropoCall :
  def __init__(self, _call):
    self._call = _call
    self.calledID=_call.getCalledId()
    self.callerID=_call.getCallerId()
    self.callerName= _call.getCallerName()
    self.calledName= _call.getCalledName()  
  
  def log (self, msg) :
    self._call.log(msg)

  def getHeader (self, name) :
    return self._call.getHeader(name)
  
  def wait (self, milliSeconds=0) :
    self._call.block(milliSeconds)
  
  def isActive(self):
    return self._call.isActive()
    
  def state(self):
    return self._call.getState().toString()
    
  def redirect(self, too):
    self._call.redirect(too)
    
  def answer (self, timeout=30) : # in second
    self._call.answer(timeout*1000)
  
  
  def reject (self) :
    self._call.reject()
  
  
  def hangup (self) :
    return self._call.hangup()
  
  
  def say (self, tts) :
    return self.prompt(tts)
    
  def ask (self, tts, options) : 
    return self.prompt(tts, options)
  
  def record(self, tts, options=None):
    oop = options
    if oop!=None:
        oop['record'] = True
    else:
        oop = {'repeat':1,'record':True, 'beep':True, 'silenceTimeout':3, 'maxTime':30,'timeout':30} 
    return prompt(tts,oop)


  def transfer(self, too, options=None):
    answerOnMedia = False
    callerID = None
    timeout = 30000
    method = "bridged"
    playrepeat = 1
    playvalue = None
    choices=None 
   
    onSuccess=None
    onError=None
    onTimeout=None
    onCallFailure=None
    onChoice=None
   
    if options : 
      if "answerOnMedia" in options: 
        answerOnMedia = options["answerOnMedia"]
      if "callerID" in options: 
        callerID = options["callerID"]
      if "callerId" in options: 
          callerID = options['callerId']
      if "timeout" in options: 
        timeout = _parseTime(options['timeout'])
      if "method" in options: 
        method = options["method"]
      if "playrepeat" in options: 
        playrepeat = options["playrepeat"] 
      if "playvalue" in options: 
        playvalue = options["playvalue"]
      if "choices" in options: 
        choices = options["choices"]
      if "onSuccess" in options: 
        onSuccess = options["onSuccess"]
      if "onError" in options: 
        onError = options["onError"]
      if "timeout" in options: 
        onTimeout = options["onTimeout"]
      if "onCallFailure" in options: 
        onCallFailure = options["onCallFailure"]
      if "onChoice" in options: 
        onChoice = options["onChoice"]

    event  = None

    try:
        _call_ = TropoCall(self._call.transfer(too, callerID, answerOnMedia,timeout, playvalue, playrepeat, choices))
        event = TropoEvent("transfer", _call_)
        if onSuccess != None:
            _handleCallBack(onSuccess,event) 
    except RuntimeException,e:
        if e.message == "Outbound call is timeout.":
            event = TropoEvent("timeout", None) # create event based on the timeout
            if onTimeout != None:
                _handleCallBack(onTimeout,event) 
        elif e.message == "Outbound call can not complete.":
            event = TropoEvent("callfailure", None)
            if onCallFailure != None:
                _handleCallBack(onCallFailure)
        elif e.message == "Outbound call cancelled.":
            event = TropoEvent("choice", None)
            if onChoice != None:
              _handleCallBack(onChoice) 
        else: 
            #puts e.message
            log(e)
            event = TropoEvent("error", e.message)
            #TODO: ??? do we need to tell the callback what kind of error it is???
            if onError != None:
                _handleCallBack(onError)
            raise e.message
    return event
   
    # if choice and record are on, return event.name = choice and event.value = grammar result and event.recordUI = recordURI
    # if choice is on but not record, return event.name = choice, event.value = grammar result, and event.recordURI = null
    # if record is on but not choice, return event.name = record, event.value = recordURI, and event.recordURI = recordURI
  def prompt(self, tts, options=None):
      grammar = ''
      timeout = 30000  #milliseconds
      onTimeout  = None
      onChoices  = None
      onBadChoice  = None
      repeat = 0
      onError  = None
      onEvent  = None
      onHangup  = None
      ttsOrUrl = ''
      bargein = True
      choiceConfidence='0.3'
      choiceMode='any' # (dtmf|speech|any) - defaults to any
  
      #record parameters
      record = False
      beep = True
      silenceTimeout = 5000  # 5 seconds
      maxTime = 30000 # 30 seconds
      onSilenceTimeout = None
      onRecord = None

      if tts != None : #make sure the ttsOrUrl is at least an empty string before calling IncomingCall
        ttsOrUrl = tts 
      #print ttsOrUrl

      if options != None: 
        if "choices" in options : #make sure the grammar is at least an empty string before calling IncomingCall
          grammar = options["choices"] 
        if "onChoice" in options:
          onChoices = options["onChoice"]
        if "onBadChoice" in options:
          onBadChoice = options["onBadChoice"]
        if "timeout" in options: 
          timeout = _parseTime(options['timeout'])
        if "onTimeout" in options:
          onTimeout = options["onTimeout"]
        if "repeat" in options:
          repeat = int(options["repeat"])
          if repeat < 0:
             repeat = 0 
        if "onError" in options:
          onError = options["onError"]
        if "onEvent" in options:
          onEvent = options["onEvent"]
        if "onHangup" in options:
          onHangup = options["onHangup"]
        if "bargein" in options:
            bargein = options["bargein"]
        if "choiceConfidence" in options:
            choiceConfidence = String.valueOf(options["choiceConfidence"])
        if "choiceMode" in options:
            choiceMode = options["choiceMode"]

        #record
        if "record" in options:
            record = options["record"]
        if "beep" in options:
            beep = options["beep"]
        if "silenceTimeout" in options:
            silenceTimeout = _parseTime(options["silenceTimeout"])
        if "maxTime" in options:
            maxTime = _parseTime(options["maxTime"])
        if "onSilenceTimeout" in options:
            onSilenceTimeout = options["onSilenceTimeout"]
        if "onRecord" in options:
            onRecord = options["onRecord"]
    
      event  = None

      for x in range(repeat+1):
        #print "timeout=%s repeat=%d x=%d" % (timeout, repeat, x)
        try:
          if record:
            result = self._call.promptWithRecord(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout, record, beep, maxTime, silenceTimeout )
            event = TropoEvent("record", result.get('recordURL'), result.get('recordURL'))
            if onRecord != None:
               _handleCallBack(onRecord,event)
            if grammar != None and grammar != '': # both choice and record are enabled
               choice = TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'))
               event = TropoEvent("choice", result.get('value'), result.get('recordURL'), choice)
               if onChoices != None:
                  _handleCallBack(onChoices,event)
          else:
            result = self._call.prompt(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout)
            choice = TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'))
            event = TropoEvent("choice", result.get('value'), result.get('recordURL'), choice)
            if onChoices != None:
               _handleCallBack(onChoices,event) 
    
          if onEvent != None:
            _handleCallBack(onEvent,event) 
          break
        except RuntimeException,e:
          #print ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + e.message
          if e.message == "NO_SPEECH" or e.message == "java.lang.RuntimeException: NoSpeech":
            event = TropoEvent("timeout", None) # create event based on the timeout
            if onTimeout != None:
              _handleCallBack(onTimeout) 
            if onEvent != None:
              _handleCallBack(onEvent,event)  
          elif e.message == "NO_MATCH":
            event = TropoEvent("badChoice", None)
            if onBadChoice != None:
              _handleCallBack(onBadChoice)
            if onEvent != None:
              _handleCallBack(onEvent,event)  
          elif e.message=="java.lang.RuntimeException: silenceTimeout":
            event = TropoEvent("silenceTimeout", None) 
            if onSilenceTimeout != None:
              _handleCallBack(onSilenceTimeout)
            if onEvent != None:
              _handleCallBack(onEvent,event)
          elif e.message == "com.mot.mrcp.MrcpDisconnectedException: Disconnect":
            event = TropoEvent("hangup", None)
            if onHangup != None:
              _handleCallBack(onHangup)  
            if onEvent != None:
              _handleCallBack(onEvent,event)
            break
          else:
            log(e)
            event = TropoEvent("error", e.message)
            #TODO: ??? do we need to tell the callback what kind of error it is???
            if onError != None:
              _handleCallBack(onError)
            if onEvent != None:
              _handleCallBack(onEvent,event) 
            raise e.message #stop the action on error
      return event


################## Global functions begin #####################

currentApp = TropoApp(appInstance) #global app object, instance of TropoApp

currentCall = None # global call object, instance of TropoCall
if incomingCall != "nullCall":
  currentCall = TropoCall(incomingCall)
  currentCall.log("currentCall is assigned to incoming " + incomingCall.toString())

def call(too, options=None):
    callerID = 'sip:Tropo@10.6.69.201'
    answerOnMedia = False
    timeout = 30000

    onAnswer =  None
    onError = None
    onTimeout = None
    onCallFailure = None

    if options != None: 
        if "onAnswer" in options:
            onAnswer = options['onAnswer']
        if "onError" in options:
            onError = options['onError']
        if "onTimeout" in options:
            onTimeout = options['onTimeout']
        if "onCallFailure" in options:
            onCallFailure = options['onCallFailure']

        if "timeout" in options: 
          timeout = _parseTime(options['timeout'])

        if "answerOnMedia" in options: 
            answerOnMedia = options['answerOnMedia']
         
        if "callerID" in options: 
            callerID = options['callerID']
        if "callerId" in options: 
            callerID = options['callerId']

    event  = None
    global currentCall

    try:
        _newCall_ = callFactory.call(callerID, too, answerOnMedia, timeout)
        _call_ = TropoCall(_newCall_)
        if currentCall == None :
            currentCall =  _call_ 
            currentCall.log("currentCall is assigned to outgoing " + _newCall_.toString())
        event = TropoEvent("answer", _call_) 
        if onAnswer != None:
            _handleCallBack(onAnswer,event) 
    except RuntimeException,e:
        if e.message == "Outbound call is timeout.":
            event = TropoEvent("timeout", None) # create event based on the timeout
            if onTimeout != None:
                _handleCallBack(onTimeout,event) 
        elif e.message == "Outbound call can not complete.":
            event = TropoEvent("callfailure", None)
            if onCallFailure != None:
                _handleCallBack(onCallFailure) 
        else: 
            #puts e.message
            log(e)
            event = TropoEvent("error", e.message)
            #TODO: ??? do we need to tell the callback what kind of error it is???
            if onError != None:
                _handleCallBack(onError)
            raise e.message
    return event

def log(msg=None):
  if currentCall!=None and currentCall.isActive():
    currentCall.log(msg)
  else:
    appInstance.log(msg)

def wait(milliSeconds=0):
  if currentCall!=None and currentCall.isActive():
    currentCall.wait(milliSeconds)
  else:
    appInstance.block(milliSeconds)
  
def redirect(too):
  currentCall.redirect(too)

def answer(timeout=30): # in second
   if currentCall != None: 
     currentCall.answer(timeout)

def reject() :
  currentCall.reject()


def hangup() :
  return currentCall.hangup()


def say(tts) :
  return currentCall.say(tts)


def ask(tts, options) :
  return currentCall.ask(tts, options)

def transfer(tts, options=None):
  return currentCall.transfer(tts, options)

def prompt(tts, options=None):
  return currentCall.prompt(tts, options)

def record(tts, options=None):
  return currentCall.record(tts, options)

######################### end shim of Python########################
