#require 'rubygems' # this will not be required in Ruby 1.9
#require 'active_support'
############################# callback event ###########################
class Hash
  # Recursively replace key names that should be strings with strings.
  def key_symbols_to_string!
    r = Hash.new
    self.each_pair do |k,v|
      r[k.to_s] = v
    end
    self.replace(r)
  end
end

def _handleCallBack(cb, ev=nil)
  begin
    if ev
      cb.call ev
    else
      cb.call
    end
  rescue Exception => e
    log(" ---- Callback Error : " + e.message)
  end 
end

def _parseTime(time)
  time = (time.to_f*1000).to_i # //convert float second to int ms
  time=0 if(time<0)
  return time
end

module Tropo

  class TropoChoice
    def initialize(concept=nil,interpretation=nil,confidence=nil,xml=nil,utterance=nil)
      @concept=concept
      @interpretation=interpretation
      @confidence=confidence
      @xml=xml
      @utterance=utterance
    end
    attr_reader :concept,:interpretation,:confidence,:xml,:utterance
  end
  
  class TropoEvent
    def initialize(name, value, recordURI=nil,choice=nil)
      @name,@value,@recordURI,@choice = name,value,recordURI,choice
    end
    attr_reader :name, :value, :recordURI, :choice
  
    def onChoice(expected, callback) 
      _handleCallBack(callback) if name == "choice" && value == expected
    end
  
    def onBadChoice(callback) 
      _handleCallBack(callback) if name == "choice" && value == "nomatch"
    end
  
    def onTimeout(callback) 
      _handleCallBack(callback) if name == "timeout"
    end 
  
    def onError(callback) 
      _handleCallBack(callback) if name == "error"
    end
  
    def onHangup(callback) 
      _handleCallBack(callback) if name == "hangup" 
    end
    
    def onRecord(callback)
      _handleCallBack(callback, self) if recordURI!=nil && recordURI != ""
    end
    
    def onSilenceTimeout(callback)
      _handleCallBack(callback) if name == "silenceTimeout"
    end
    
  end
  
  ############################# tropoCall ###########################
  class TropoApp
    def initialize(app)
      @app = app
      @baseDir=app.getApp().getBaseDir()
    end
    attr_reader :app, :baseDir
  end
  
  class TropoCall
    def initialize(call)
      @call = call
      @calledID=call.getCalledId()
      @callerID=call.getCallerId()
      @callerName= call.getCallerName()
      @calledName= call.getCalledName()
    end
    attr_reader :call, :calledID, :callerID, :callerName, :calledName
  
    def log (msg) 
      call.log(msg)
    end
    
    def getHeader(name)
      return call.getHeader(name)
    end
    
    def wait(milliSeconds=0)
      call.block(milliSeconds)
    end

    def startCallRecording (uri, format='audio/wav', key='', keyUri='')
      call.startCallRecording(uri, format, key, keyUri)
    end
        

    def stopCallRecording ()
      call.stopCallRecording()
    end
        
    def state()
      return call.getState().toString()
    end
  
    def isActive()
      return call.isActive()
    end
  
    def answer (timeout=30)  # in second
      call.answer(timeout*1000)
    end
  
    def redirect (too) 
      call.redirect(too)
    end
  
    def reject () 
      call.reject()
    end
  
    def hangup () 
      return call.hangup()
    end
  
    def say (tts) 
      return prompt(tts)
    end
  
    def ask (tts, options) 
      return prompt(tts, options)
    end
  
    def record(tts, options=nil)
      oop = options
      if(oop!=nil)
        oop['record']=true
      else
        oop = {'repeat'=>1,'record'=>true, 'beep'=>true, 'silenceTimeout'=>3, 'maxTime'=>30,'timeout'=>30}
      end
      return prompt(tts, oop)
    end  
  
    def transfer(too, options=nil)
      answerOnMedia = false
      callerID = nil
      timeout = 30000
      method = "bridged"
      playrepeat = 1
      playvalue = nil
      choices=nil 
      
      onSuccess=nil
      onError=nil
      onTimeout=nil
      onCallFailure=nil
      onChoice=nil #only used to accept a DTMF input to cancel the transfer
      
      if options != nil
        options.key_symbols_to_string!
        answerOnMedia = options["answerOnMedia"] if options["answerOnMedia"] != nil 
        callerID = options['callerID'] if options['callerID'] != nil
        callerID = options['callerId'] if options['callerId'] != nil
        timeout = _parseTime(options["timeout"]) if options["timeout"] != nil
        method = options["method"]
        playrepeat = options["playrepeat"] if options["playrepeat"]
        playvalue = options["playvalue"] if options["playvalue"]
        choices = options["choices"]
  
        onSuccess = options["onSuccess"]
        onError = options["onError"]
        onTimeout = options["onTimeout"]
        onCallFailure = options["onCallFailure"]
        onChoice = options["onChoice"]
      end
      event  = nil
  
      begin
        _call_ = TropoCall.new(call.transfer(too, callerID, answerOnMedia,timeout, playvalue, playrepeat, choices))
        event = TropoEvent.new("transfer", _call_)
        _handleCallBack(onSuccess,event) if onSuccess != nil
      rescue Exception => e
        if e.message == "com.voxeo.tropo.ErrorException: Outbound call is timeout."
          event = TropoEvent.new("timeout", nil) # create event based on the timeout
          _handleCallBack(onTimeout,event) if onTimeout != nil
        elsif e.message == "com.voxeo.tropo.ErrorException: Outbound call can not complete."
          event = TropoEvent.new("callfailure", nil)
          _handleCallBack(onCallFailure,event) if onCallFailure != nil 
        elsif e.message == "com.voxeo.tropo.ErrorException: Outbound call cancelled."
          event = TropoEvent.new("choice", nil)
          _handleCallBack(onChoice) if onChoice != nil 
        else 
          #puts e.message
          call.log(e) rescue puts e.message
          event = TropoEvent.new("error", e.message)
          #TODO: ??? do we need to tell the callback what kind of error it is???
          _handleCallBack(onError) if onError != nil
          raise e.message
        end
      end
      return event
    end
  
    # if choice and record are on, return event.name = choice and event.value = grammar result and event.recordUI = recordURI
    # if choice is on but not record, return event.name = choice, event.value = grammar result, and event.recordURI = null
    # if record is on but not choice, return event.name = record, event.value = recordURI, and event.recordURI = recordURI
    def prompt (tts, options=nil)
      grammar = ''
      timeout = 30000  #milliseconds
      onTimeout  = nil
      onChoices  = nil
      repeat = 0
      onError  = nil
      onEvent  = nil
      onHangup  = nil
      onBadChoice = nil
      ttsOrUrl = ''
      bargein = true
      choiceConfidence='0.3'
      choiceMode='any' # (dtmf|speech|any) - defaults to any
  
      #record parameters
      record = false
      beep = true
      silenceTimeout = 5000  # 5 seconds
      maxTime = 30000 # 30 seconds
      onSilenceTimeout = nil
      onRecord = nil
  
      recordURI = ''
      recordFormat = 'audio/wav'
      httpMethod = 'POST'
  
      ttsOrUrl = tts if tts != nil  #make sure the ttsOrUrl is at least an empty string before calling IncomingCall
      #puts ttsOrUrl
  
      if options != nil
        options.key_symbols_to_string!
        grammar = options["choices"] if options["choices"]  #make sure the grammar is at least an empty string before calling IncomingCall
        onChoices = options["onChoice"]
        timeout = _parseTime(options["timeout"]) if options["timeout"] != nil 
        onTimeout = options["onTimeout"]
        if(options["repeat"] != nil)
          repeat = options["repeat"].to_i
          repeat = 0 if repeat < 0
        end    
        onError = options["onError"]
        onEvent = options["onEvent"]
        onHangup = options["onHangup"]
        onBadChoice = options["onBadChoice"]
        bargein = options["bargein"] if options["bargein"] != nil
        choiceConfidence = options["choiceConfidence"].to_s if options["choiceConfidence"] != nil
        choiceMode = options["choiceMode"] if options["choiceMode"] != nil
        #record
        record = options["record"] if options["record"] != nil
        beep = options["beep"] if options["beep"] != nil
        silenceTimeout = _parseTime(options["silenceTimeout"]) if options["silenceTimeout"] != nil
        maxTime = _parseTime(options["maxTime"]) if options["maxTime"] != nil
        onSilenceTimeout = options["onSilenceTimeout"]
        onRecord = options["onRecord"]
        
        recordURI = options["recordURI"] if options["recordURI"] != nil
        recordFormat = options["recordFormat"] if options["recordFormat"] != nil
        httpMethod = options["httpMethod"] if options["httpMethod"] != nil
      end
  
      event  = nil
  
      for x in 0..repeat
        #puts "timeout=#timeoutend repeat=#repeatend x=#xend"
        begin
          if record
            result = call.promptWithRecord(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout, record, beep, maxTime, silenceTimeout, recordURI, recordFormat, httpMethod )
            event = TropoEvent.new("record", result.get('recordURL'), result.get('recordURL'))
            _handleCallBack(onRecord,event) if onRecord != nil
            if grammar != nil && grammar != '' # both choice and record are enabled
               choice = TropoChoice.new(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
               event = TropoEvent.new("choice", result.get('value'), result.get('recordURL'), choice);
               _handleCallBack(onChoices,event) if onChoices != nil
            end
          else
            result = call.prompt(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout)
            choice = TropoChoice.new(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
            event = TropoEvent.new("choice", result.get('value'), result.get('recordURL'), choice);
            _handleCallBack(onChoices,event) if onChoices != nil
          end
          _handleCallBack(onEvent,event) if onEvent != nil
          break
        rescue Exception => e
          if e.message == "com.voxeo.tropo.ErrorException: NO_SPEECH" || e.message == "com.voxeo.tropo.FatalException: java.lang.RuntimeException: NoSpeech"
            event = TropoEvent.new("timeout", nil) # create event based on the timeout
            _handleCallBack(onTimeout,event) if onTimeout != nil
            _handleCallBack(onEvent,event) if onEvent != nil 
          elsif e.message == "com.voxeo.tropo.ErrorException: NO_MATCH"
            event = TropoEvent.new("badChoice", nil)
            _handleCallBack(onBadChoice,event) if onBadChoice != nil 
            _handleCallBack(onEvent,event) if onEvent != nil 
          elsif e.message == "java.lang.RuntimeException: java.lang.RuntimeException: silenceTimeout"
            event = TropoEvent.new("silenceTimeout", nil)
            _handleCallBack(onSilenceTimeout) if onSilenceTimeout != nil
            _handleCallBack(onEvent,event) if onEvent != nil 
          elsif e.message == "com.voxeo.tropo.FatalException: com.mot.mrcp.MrcpDisconnectedException: Disconnect"
            event = TropoEvent.new("hangup", nil)
            _handleCallBack(onHangup,event) if onHangup != nil
            _handleCallBack(onEvent,event) if onEvent != nil 
            break
          else 
            call.log(e) rescue puts e.message
            event = TropoEvent.new("error", e.message)
            #TODO: ??? do we need to tell the callback what kind of error it is???
            _handleCallBack(onError) if onError != nil
            _handleCallBack(onEvent,event) if onEvent != nil 
            raise e.message #stop the action on error
          end
        end
      end
      return event
    end
  end
  
end
######################### end definition of Tropo###########################
