#require 'rubygems' # this will not be required in Ruby 1.9
#require 'active_support'
require 'tropo_common'
###################### Global functions begin ###########################

$currentApp = Tropo::TropoApp.new($appInstance) #global app object, instance of TropoApp

$currentCall = nil # global call object, instance of TropoCall
if $incomingCall != "nullCall"
  $currentCall = Tropo::TropoCall.new($incomingCall) 
  $currentCall.log("currentCall is assigned to incoming " + $incomingCall.toString())
end


def call(too, options=nil)
  callerID = 'sip:Tropo@10.6.69.201'
  answerOnMedia = false
  timeout = 30000

  onAnswer = nil
  onError = nil
  onTimeout = nil
  onCallFailure = nil
  
  recordURI = ''
  recordFormat='audio/wav'

  if options != nil
    options.key_symbols_to_string!
    onAnswer = options['onAnswer']
    onError = options['onError']
    onTimeout = options['onTimeout']
    onCallFailure = options['onCallFailure']

    timeout = _parseTime(options["timeout"]) if options["timeout"] != nil 

    answerOnMedia = options['answerOnMedia'] if options['answerOnMedia'] != nil
    callerID = options['callerID'] if options['callerID'] != nil
    callerID = options['callerId'] if options['callerId'] != nil
    recordURI = options['recordURI'] if options['recordURI'] != nil
    recordFormat = options['recordFormat'] if options['recordFormat'] != nil
  end                                                              

  event  = nil

  begin
    _newCall_ = $callFactory.call(callerID, too, answerOnMedia, timeout, recordURI, recordFormat)
    _call_ = Tropo::TropoCall.new(_newCall_)
    if $currentCall == nil
      $currentCall =  _call_ 
      $currentCall.log("currentCall is assigned to outgoing " + _newCall_.toString())
    end
    event = Tropo::TropoEvent.new("answer", _call_)
    _handleCallBack(onAnswer,event) if onAnswer != nil
  rescue Exception => e
    if e.message == "com.voxeo.tropo.ErrorException: Outbound call is timeout."
      event = Tropo::TropoEvent.new("timeout", nil) # create event based on the timeout
      _handleCallBack(onTimeout,event) if onTimeout != nil
    elsif e.message == "com.voxeo.tropo.ErrorException: Outbound call can not complete."
      event = Tropo::TropoEvent.new("callfailure", nil)
      _handleCallBack(onCallFailure,event) if onCallFailure != nil 
    else 
      #puts e.message
      log(e)
      event = Tropo::TropoEvent.new("error", e.message)
      #TODO: ??? do we need to tell the callback what kind of error it is???
      _handleCallBack(onError) if onError != nil
      raise e.message
    end
  end
  return event
end

def wait(milliSeconds=0)
  $currentCall.wait(milliSeconds)
end

def log(msg=nil)
  if $currentCall!=nil && $currentCall.isActive
    $currentCall.log(msg)
  else
    $appInstance.log(msg)
  end
end

def wait(milliSeconds=0)
  if $currentCall!=nil && $currentCall.isActive
    $currentCall.wait(milliSeconds)
  else
    $appInstance.block(milliSeconds)
  end
end

def answer(timeout=30) # in second
  $currentCall.answer(timeout) if $currentCall
end
       
def startCallRecording (uri, format='audio/wav', key='', keyUri='') {
  $currentCall.startCallRecording(uri, format, key,keyUri) if $currentCall
end

def stopCallRecording () {
  $currentCall.stopCallRecording() if $currentCall
end

def redirect(too)
  $currentCall.redirect(too)
end

def reject 
  $currentCall.reject
end

def hangup
  $currentCall.hangup
end

def transfer(tts, options=nil)
  return $currentCall.transfer(tts, options)
end

def prompt(tts, options=nil)
  return $currentCall.prompt(tts, options)
end

def record(tts, options=nil)
  return $currentCall.record(tts, options)
end

alias say prompt

alias ask prompt

######################### end shim of Ruby###########################
