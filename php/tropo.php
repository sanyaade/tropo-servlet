<?php

/////////////////////////// callback event/////////////////////////

function _parseTime($time){ // convert float second to integer milliseconds
    return round($time*1000);
}  // function _parseTime

function _handleCallBack($cb, $ev){
    try {
        if ($ev) {
            $cb($ev);
        } else {
            $cb();
        }  // if
    } catch (Exception $e){
        _log(" ---- Callback Error : " . $e->getMessage() . "\n");
    }  // try
}  // function _handleCallBack

class TropoApp {
    public $app = null;
    public $baseDir = null;
    
    function TropoApp($app) {
        $this->app = $app;
        $this->baseDir = $app->getApp()->getBaseDir();
    }  // function TropoApp
}

class TropoChoice {
    public $concept=null;
    public $interpretation=null;
    public $confidence=null;
    public $xml=null;
    public $utterance=null;
    
  function TropoChoice($concept=null,$interpretation=null,$confidence=null,$xml=null,$utterance=null){
    $this->concept=$concept;
    $this->interpretation=$interpretation;
    $this->confidence=$confidence;
    $this->xml=$xml;
    $this->utterance=$utterance;
  }
}

class TropoEvent {
    public $name      = null;
    public $value     = null;
    public $recordURI = null;
    public $choice = null;
  
    function TropoEvent($name, $value, $recordURI=null, $choice=null) {
        $this->name = $name;
        $this->value = $value;
        $this->recordURI = $recordURI;
        $this->choice = $choice;
    }  // function TropoEvent

    function onChoice($expected, $callback) {
        if ( ($this->name == "choice") &&  ($this->value == $expected) ) {
            $callback();
        }
    }  // function onChoice

    function onBadChoice($callback) {
        if ( ($this->name == "choice") && ($this->value == "nomatch") ) {
            $callback();
        }
    }  // function onBadChoice

    function onTimeout($callback) {
        if ($this->name == "timeout") {
            $callback();
        }
    }  // function onTimeout

    function onError($callback) {
        if ($this->name == "error") {
            $callback();
        }
    }  // function onError

    function onHangup($callback) {
        if ($this->name == "hangup") {
            $callback();
        }
    }  // function onHangup

    function onRecord($callback) {
        if ( ($this->recordURI != null) && ($this->recordURI != "") ) {
            $callback();
        }    
    }  // function onRecord

    function onSilenceTimeout($callback) {
        if ($this->name == "silenceTimeout") {
            $callback();
        }    
    }  // function onSilenceTimeout

    public function __toString() {
        ob_start();
        var_export($this);
        $outstr = ob_get_contents();
        ob_end_clean();
        
        return $outstr;
    }  //function __toString
} // class TropoEvent
//////////////////////// tropoCall //////////////////
class TropoCall {
    public $_call_     = null;
    public $calledID   = null;
    public $callerID   = null;
    public $callerName = null;
    public $calledName = null;
    
    public $app        = null;
    
    function TropoCall($call) {
        $this->_call_     = $call;
        $this->calledID   = $call->getCalledId();
        $this->callerID   = $call->getCallerId();
        $this->callerName = $call->getCallerName();
        $this->calledName = $call->getCalledName();
    }  // function TropoCall

    function _log($msg) {
        $this->_call_->log($msg);
    }  //function _log
    
    function getHeader($name) {
        return $this->_call_->getHeader($name);
    }
    
    function wait($milliSeconds=0) {
        $this->_call_->block($milliSeconds);
    }  // function wait
    
    function state() {
        return $this->_call_->getState();
    }  // function state
    
    function isActive() {
        return $this->_call_->isActive();
    }  // function isActive
    
    function redirect($too) {
        $this->_call_->redirect($too);
    }  // function redirect
    
    function answer($timeout=30) { // in second
        $this->_call_->answer($timeout * 1000);
    }  // function answer
    
    function startCallRecording ($uri, $format="audio/wav", $key=null, $keyUri=null) {
        $this->_call_->startCallRecording($uri, $format, $key, $keyUri);
    }

    function stopCallRecording () {
        $this->_call_->stopCallRecording();
    }

    function reject() {
        $this->_call_->reject();
    }  // function reject
    
    function hangup() {
        return $this->_call_->hangup();
    }  // function hangup
    
    function say($tts) {
        return $this->prompt($tts);
    }  // function say
    
    function ask($tts, $options) {
        return $this->prompt($tts, $options);
    }  // function ask
    
    function record($tts, $options){
        $oop = $options;
        if($oop!=null){ 
            $oop['record'] = true;
        } else {
            $oop = array('repeat'=>1,
                         'record'=>true, 
                         'beep'=>true, 
                         'silenceTimeout'=>3, 
                         'maxTime'=>30, 
                         'timeout'=>30); 
        }  // if
        return $this->prompt($tts,$oop);
    }  // function record
    
    function transfer($too, $options=null){
        $answerOnMedia = false;
        $callerID      = null;
        $timeout       = 30000;
        $method        = "bridged";
        $playrepeat    = 1;
        $playvalue     = null;
        $choices       = null; 
        
        $onSuccess     = null;
        $onError       = null;
        $onTimeout     = null;
        $onCallFailure = null;
        $onChoice      = null;
        
        if ($options != null) { 
            if ($options["answerOnMedia"] != null) {
                $answerOnMedia = $options["answerOnMedia"];
            }
            if($options["timeout"] != null) {
                $timeout = _parseTime($options["timeout"]); //convert float second to ms
            }
            if ($options["playrepeat"]!=null) {
                $playrepeat = $options["playrepeat"] ;
            }
            
            $method    = $options["method"];
            if($options['callerID'] != null){
               $callerID = $options['callerID'];
            } 
            if($options['callerId'] != null){
               $callerID = $options['callerId'];
            } 
            $playvalue = $options["playvalue"];
            $choices   = $options["choices"];
            
            $onSuccess     = $options["onSuccess"];
            $onError       = $options["onError"];
            $onTimeout     = $options["onTimeout"];
            $onCallFailure = $options["onCallFailure"];
            $onChoice      = $options["onChoice"];
        }  // if $options
        
        $___event___  = null;
    
        try{
            $_ncall_ = new TropoCall($this->_call_->transfer($too, 
                                                            $callerID, 
                                                            $answerOnMedia, 
                                                            $timeout, 
                                                            $playvalue, 
                                                            $playrepeat, 
                                                            $choices));
            $___event___ = new TropoEvent("transfer", $_ncall_);
            if($onSuccess != null){
                _handleCallBack($onSuccess, $___event___);
            }
        } catch (Exception $e) {
            if($e->getMessage() == "com.voxeo.tropo.ErrorException: Outbound call is timeout.") {
                $___event___ = new TropoEvent("timeout", null); // create event based on the timeout
                if ($onTimeout != null) {
                    _handleCallBack($onTimeout, $___event___);
                }
            } elseif($e->getMessage() == "com.voxeo.tropo.ErrorException: Outbound call can not complete.") {
                $___event___ = new TropoEvent("callfailure", null);
                if ($onCallFailure != null) {
                    _handleCallBack($onCallFailure, $___event___); 
                }
            } elseif($e->getMessage() == "com.voxeo.tropo.ErrorException: Outbound call cancelled.") {
                $___event___ = new TropoEvent("choice", null);
                if ($onChoice != null) {
                    _handleCallBack($onChoice, $___event___);
                }
            } else { 
                //puts e.message
                _log($e->getMessage());
                $___event___ = new TropoEvent("error", $e->getMessage() );
                //TODO: ??? do we need to tell the callback what kind of error it is???
                if( $onError != null){
                    _handleCallBack($onError);
                }
                throw $e;
            }  // if message checks
        }  // try-catch
        return $___event___;
    } //function transfer
    
    //if choice and record are on, return event.name = choice and event.value = grammar result and event.recordUI = recordURI
    //if choice is on but not record, return event.name = choice, event.value = grammar result, and event.recordURI = null
    //if record is on but not choice, return event.name = record, event.value = recordURI, and event.recordURI = recordURI
    function prompt($tts, $options=null) {
        $grammar   = ""; // don't use null here, otherwise java object will get a string of "null" intead of a null object
        $timeout   = 30000;  //milliseconds
        $onTimeout = null;
        $onChoices = null;
        $repeat    = 0;
        $ttsOrUrl  = "";
        $bargein  = true;
    $choiceConfidence="0.3";
    $choiceMode="any"; // (dtmf|speech|any) - defaults to any
        
        $onError     = null;
        $onEvent     = null;
        $onHangup    = null;
        $onBadChoice = null;
        
        //record parameters
        $record         = false;
        $beep           = true;
        $silenceTimeout = 5000;  // 5 seconds
        $maxTime        = 30000; // 30 seconds
        
        $onSilenceTimeout = null;
        $onRecord         = null;
    
        if ($tts != null){//make sure the ttsOrUrl is at least an empty string before calling IncomingCall
            $ttsOrUrl = $tts;
        }
    
        if ($options) {
            if ($options["choices"]) { //make sure the grammar is at least an empty string before calling IncomingCall
                $grammar = $options["choices"]; 
            }
            if($options["timeout"] != null) {
                $timeout = _parseTime($options["timeout"]); //convert float second to ms
            }
            if($options["repeat"] != null){
                $repeat = $options["repeat"];
            }
            if ($options["bargein"] != null){
                $bargein = $options["bargein"]; 
            }
            if ($options["choiceConfidence"] != null){
                $choiceConfidence = $options["choiceConfidence"]; 
            }
            if ($options["choiceMode"] != null){
                $choiceMode = $options["choiceMode"]; 
            }
            
            $onChoices   = $options["onChoice"];
            $onTimeout   = $options["onTimeout"];
            $onError     = $options["onError"];
            $onEvent     = $options["onEvent"];
            $onHangup    = $options["onHangup"];
            $onBadChoice = $options["onBadChoice"];
            
            //record
            if($options["record"] != null){
                $record = $options["record"]; 
            }
            if($options["beep"] != null){
                $beep = $options["beep"]; 
            }
            if($options["silenceTimeout"] != null){
                $silenceTimeout = _parseTime($options["silenceTimeout"]); 
            }
            if($options["maxTime"] != null){
                $maxTime = _parseTime($options["maxTime"]); 
            }

            $onSilenceTimeout = $options["onSilenceTimeout"];
            $onRecord         = $options["onRecord"];

        } // if ($options)
            
        $___event___ = null;
        
        for ($x=0; $x<=$repeat; $x++){
            try{
                if ($record) {
                    $result = $this->_call_->promptWithRecord(  $ttsOrUrl, 
                                                                $bargein, 
                                                                $grammar, 
                                                                $choiceConfidence, 
                                                                $choiceMode,
                                                                $timeout, 
                                                                $record, 
                                                                $beep, 
                                                                $maxTime, 
                                                                $silenceTimeout );

                    $___event___ = new TropoEvent("record", $result['recordURL'], $result['recordURL']);
                    if ($onRecord != null) {
                        _handleCallBack($onRecord, $___event___);
                    }
                    if ( ($grammar != null) && ($grammar != "") ) { // both choice and record are enabled
                        $___choice___ = new TropoChoice($result['concept'],$result['interpretation'],$result['confidence'],$result['xml'],$result['utterance']);
                        $___event___ = new TropoEvent("choice", $result['value'], $result['recordURL'], $___choice___);
                        if ($onChoices != null) {
                            _handleCallBack($onChoices, $___event___);
                        }
                    }
                } else {
                    $result = $this->_call_->prompt($ttsOrUrl, 
                                                    $bargein, 
                                                    $grammar, 
                                                    $choiceConfidence, 
                                                    $choiceMode,
                                                    $timeout);

                    $___choice___ = new TropoChoice($result['concept'],$result['interpretation'],$result['confidence'],$result['xml'],$result['utterance']);
                    $___event___ = new TropoEvent("choice", $result['value'], $result['recordURL'], $___choice___);
                    if ($onChoices != null) {
                        _handleCallBack($onChoices,$___event___);
                    }
                } // if ($record)
                
                if ($onEvent != null) {
                    _handleCallBack($onEvent, $___event___);
                }
                break;
            } catch (Exception $e){
                if ( ($e->getMessage()=="com.voxeo.tropo.core.Call.prompt: NO_SPEECH") || 
                     ($e->getMessage() =="com.voxeo.tropo.FatalException: java.lang.RuntimeException: NoSpeech") ) {
                    $___event___ = new TropoEvent("timeout", null); // create event based on the timeout
                    if ($onTimeout != null){
                        _handleCallBack($onTimeout, $___event___);
                    }
    
                    if ($onEvent != null) {
                        _handleCallBack($onEvent, $___event___);
                    }
                } elseif ($e->getMessage() =="com.voxeo.tropo.core.Call.prompt: NO_MATCH") {
                    $___event___ = new TropoEvent("badChoice", null);
                    if ($onBadChoice != null) {
                        _handleCallBack($onBadChoice, $___event___);
                    }
                    if ($onEvent != null) {
                        _handleCallBack($onEvent, $___event___);
                    }
                } elseif ($e->getMessage() =="java.lang.RuntimeException: silenseTime") {
                    $___event___ = new TropoEvent("silenceTimeout", null);
    
                    if ($onSilenceTimeout != null) {
                        _handleCallBack($onSilenceTimeout);
                    }
                    if ($onEvent != null) {
                        _handleCallBack($onEvent, $___event___);
                    }
                } elseif ($e->getMessage() =="com.voxeo.tropo.core.Call.prompt: com.mot.mrcp.MrcpDisconnectedException: Disconnect") {
                    $___event___ = new TropoEvent("hangup", null);
    
                    if($onHangup != null){
                        _handleCallBack($onHangup,$___event___);
                    }
                    if ($onEvent != null) {
                        _handleCallBack($onEvent,$___event___);
                    }
                    break;
                } else {
                    _log($e->getMessage());
                    $___event___ = new TropoEvent("error", $e->getMessage());
    
                    //TODO: ??? do we need to tell the callback what kind of error it is???
                    if ($onError != null){
                        _handleCallBack($onError);
                    }
                    if ($onEvent != null) {
                        _handleCallBack($onEvent, $___event___);
                    }
                    
                    throw $e;
                }  // if message checks
            }  // try - catch
        } // for    
    
        return $___event___;
    } // function prompt
    
    public function __toString() {
        ob_start();
        var_export($this);
        $outstr = ob_get_contents();
        ob_end_clean();
        
        return $outstr;
    } // function __toString
    
} //class TropoCall
////////////////// Global functions begin ///////////////////////

function call($too, $options){
    $callerID      = 'sip:Tropo@10.6.69.201';
    $answerOnMedia = false;
    $timeout       = 30000;

    $onAnswer      = null;
    $onError       = null;
    $onTimeout     = null;
    $onCallFailure = null;

    $recordUri = '';
    $recordFormat='audio/wav';

    if ($options) {
        $onAnswer      = $options['onAnswer'];
        $onError       = $options['onError'];
        $onTimeout     = $options['onTimeout'];
        $onCallFailure = $options['onCallFailure'];

        if ($options["timeout"] != null) {
            $timeout = _parseTime($options["timeout"]); //convert float second to ms
        }

        if($options['answerOnMedia'] != null){
            $answerOnMedia = $options['answerOnMedia'];
        } 
        
        if($options['callerID'] != null){
            $callerID = $options['callerID'];
        } 
        if($options['callerId'] != null){
            $callerID = $options['callerId'];
        } 
        if($options['recordUri'] != null){
            $recordUri = $options['recordUri'];
        } 
        if($options['recordFormat'] != null){
            $recordFormat = $options['recordFormat'];
        } 
    }  // if $options

    $___event___  = null;

    try {
        //???  what is callFactory?  it's in all the shims
        $_newCall_ = $callFactory->call($callerID, $too, $answerOnMedia, $timeout, $recordUri, $recordFormat);
        $_call_ = new TropoCall($_newCall_);
        
        if ($currentCall == null) {
            $currentCall = $_call_;
            $currentCall->_log("currentCall is assigned to outgoing " . $_newCall_);
        }  // if
        
        $___event___ = new TropoEvent("answer", $_call_); 

        if($onAnswer != null) {
            _handleCallBack($onAnswer, $___event___);
        }
    } catch (Exception $e) {
        if ($e->getMessage() == "com.voxeo.tropo.ErrorException: Outbound call is timeout.") {
            $___event___ = new TropoEvent("timeout", null); // create event based on the timeout
            if ($onTimeout != null) {
                _handleCallBack($onTimeout, $___event___); 
            }
        } elseif ($e->getMessage()  == "com.voxeo.tropo.ErrorException: Outbound call can not complete.") {
            $___event___ = new TropoEvent("callfailure", null);
            if ($onCallFailure != null) {
                _handleCallBack($onCallFailure, $___event___);
            }
        } else { 
            //puts e.message
            _log( $e->getMessage() );
            $___event___ = new TropoEvent("error", $e->getMessage());
            //TODO: ??? do we need to tell the callback what kind of error it is???
            if ($onError != null) {
                _handleCallBack($onError);
            }
            throw $e;
        }  // if message checks
    }  // try-catch
    return $___event___;
}  // function call


function _log($msg) {
  GLOBAL $currentCall, $appInstance;
  
  if (($currentCall!=null) && ($currentCall->isActive())) {
    $currentCall->_log($msg);
  } else {
    $appInstance->log($msg);
  }
}  // function _log

function wait($milliSeconds=0) {
  GLOBAL $currentCall, $appInstance;
  
  if (($currentCall!=null) && ($currentCall->isActive())) {
    $currentCall->wait($milliSeconds);
  } else {
    $appInstance->block($milliSeconds);
  }
}  // function wait

function redirect($too) {
  GLOBAL $currentCall;
  
  $currentCall->redirect($too);
}  // function redirect

function answer($timeout=30) { // in second
  GLOBAL $currentCall;
  
  if ($currentCall!=null) {
    $currentCall->answer($timeout);
  }
}  // function answer
                    
function startCallRecording ($uri, $format, $key, $keyUri) {       
  GLOBAL $currentCall;

  if ($currentCall!=null) {
    $currentCall->startCallRecording($uri, $format, $key, $keyUri);
  }    
}  // function startCallRecording
                    
function stopCallRecording () {       
  GLOBAL $currentCall;

  if ($currentCall!=null) {
    $currentCall->stopCallRecording();
  }    
}  // function stopCallRecording

function reject() {
  GLOBAL $currentCall;
  
  $currentCall->reject();
}  // function reject

function hangup() {
  GLOBAL $currentCall;
  
  return $currentCall->hangup();
}  // function hangup

function say($tts) {
  GLOBAL $currentCall;
  
  return $currentCall->say($tts);
}  // function say

function ask($tts, $options=null) {
  GLOBAL $currentCall;
  
  return $currentCall->ask($tts, $options);
}  // function ask

function transfer($tts, $options=null){
  GLOBAL $currentCall;
  
  return $currentCall->transfer($tts, $options);
}  // function transfer

function prompt($tts, $options=null){
  GLOBAL $currentCall;

  return $currentCall->prompt($tts, $options);
}  // function prompt

function record($tts, $options=null){
  GLOBAL $currentCall;
  
  return $currentCall->record($tts, $options);
}  //function record

////////////////// MAIN begin ///////////////////////
$currentApp = new TropoApp($appInstance); //global app object, instance of TropoApp

$currentCall = null; //global call object, instance of TropoCall
if ( $incomingCall != "nullCall" ) {
  $currentCall = new TropoCall($incomingCall);
  $currentCall->_log("currentCall is assigned to incoming " . $incomingCall);
}


////////////////////end shim of PHP//////////////////
?>