///////////////////////////JSON/////////////////////////////////////
/*
Copyright (c) 2005 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/*
    The global object JSON contains two methods.

    JSON.stringify(value) takes a JavaScript value and produces a JSON text.
    The value must not be cyclical.

    JSON.parse(text) takes a JSON text and produces a JavaScript value. It will
    return false if there is an error.
*/
var JSON = function () {
    var m = {
            '\b': '\\b',
            '\t': '\\t',
            '\n': '\\n',
            '\f': '\\f',
            '\r': '\\r',
            '"' : '\\"',
            '\\': '\\\\'
        },
        s = {
            'boolean': function (x) {
                return String(x);
            },
            number: function (x) {
                return isFinite(x) ? String(x) : 'null';
            },
            string: function (x) {
                if (/["\\\x00-\x1f]/.test(x)) {
                    x = x.replace(/([\x00-\x1f\\"])/g, function(a, b) {
                        var c = m[b];
                        if (c) {
                            return c;
                        }
                        c = b.charCodeAt();
                        return '\\u00' +
                            Math.floor(c / 16).toString(16) +
                            (c % 16).toString(16);
                    });
                }
                return '"' + x + '"';
            },
            object: function (x) {
                if (x) {
                    var a = [], b, f, i, l, v;
                    if (x instanceof Array) {
                        a[0] = '[';
                        l = x.length;
                        for (i = 0; i < l; i += 1) {
                            v = x[i];
                            f = s[typeof v];
                            if (f) {
                                v = f(v);
                                if (typeof v == 'string') {
                                    if (b) {
                                        a[a.length] = ',';
                                    }
                                    a[a.length] = v;
                                    b = true;
                                }
                            }
                        }
                        a[a.length] = ']';
                    } else if (x instanceof Object) {
                      if (x.hashCode) return s.string(''+x.toString());
                        a[0] = '{';
                        for (i in x) {
                            v = x[i];
                            f = s[typeof v];
                            if (f) {
                                v = f(v);
                                if (typeof v == 'string') {
                                    if (b) {
                                        a[a.length] = ',';
                                    }
                                    a.push(s.string(i), ':', v);
                                    b = true;
                                }
                            }
                        }
                        a[a.length] = '}';
                    } else {
                        return;
                    }
                    return a.join('');
                }
                return 'null';
            }
        };
    return {
        copyright: '(c)2005 JSON.org',
        license: 'http://www.JSON.org/license.html',
/*
    Stringify a JavaScript value, producing a JSON text.
*/
        stringify: function (v) {
            var f = s[typeof v];
            if (f) {
                v = f(v);
                if (typeof v == 'string') {
                    return v;
                }
            }
            return null;
        },
/*
    Parse a JSON text, producing a JavaScript value.
    It returns false if there is a syntax error.
*/
        parse: function (text) {
            try {
                return !(/[^,:{}\[\]0-9.\-+Eaeflnr-u \n\r\t]/.test(
                        text.replace(/"(\\.|[^"\\])*"/g, ''))) &&
                    eval('(' + text + ')');
            } catch (e) {
                return false;
            }
        }
    };
}();

//////////////////////////// callback event/////////////////////////

function _parseTime(time){ // convert float second to integer milliseconds
  return Math.max(0, parseInt(parseFloat(time)*1000));
}

function _handleCallBack(cb, ev){
  try {
    if(ev){
      cb(ev);
    }
    else{
      cb();
    }
  } catch (e){
    log(" ---- Callback Error : " + e.message);
  }
}

function TropoChoice(concept,interpretation,confidence,xml,utterance) {
  this.concept=concept;
  this.interpretation=interpretation;
  this.confidence=confidence;
  this.xml=xml;
  this.utterance=utterance;
}

function __voxeo__prompt__event(name, value, recordURI, choice) {
  this.name = name;
  this.value = value;
  this.recordURI = recordURI;
  this.choice = choice;
}
__voxeo__prompt__event.prototype.onChoice = function (expected, callback) {
  if (this.name == "choice") {
    if (this.value == expected) {
      _handleCallBack(callback);
    }
  }
}

__voxeo__prompt__event.prototype.onBadChoice = function (callback) {
  if (this.name == "choice") {
    if (this.value == "nomatch") {
      _handleCallBack(callback);
    }
  }
}

__voxeo__prompt__event.prototype.onTimeout = function (callback) {
  if (this.name == "timeout") {
    _handleCallBack(callback);
  }
}

__voxeo__prompt__event.prototype.onError = function (callback) {
  if (this.name == "error") {
    _handleCallBack(callback);
  }
}

__voxeo__prompt__event.prototype.onHangup = function (callback) {
  if (this.name == "hangup") {
    _handleCallBack(callback);
  }
}

__voxeo__prompt__event.prototype.onSilenceTimeout = function (callback) {
  if (this.name == "silenceTimeout") {
    _handleCallBack(callback);
  }
}

__voxeo__prompt__event.prototype.onRecord = function (callback) {
  if (this.recordURI!=null && this.recordURI!='') {
    _handleCallBack(callback,this);
  }
}

//////////////////////// tropoCall //////////////////

function TropoApp(app) {
  this.app = app;
  this.baseDir=app.getApp().getBaseDir();
}

function TropoCall(call) {
  this._call_ = call;
  this.calledID=call.getCalledId();
  this.callerID=call.getCallerId();
  this.callerName= call.getCallerName();
  this.calledName= call.getCalledName();
}

TropoCall.prototype.log = function (msg) {
  this._call_.log(msg);
}
TropoCall.prototype.getHeader = function (name) {
  return this._call_.getHeader(name);
}
TropoCall.prototype.wait = function (milliSeconds) {
  if (!milliSeconds || milliSeconds == null) {
    milliSeconds = 0;
  }
  this._call_.block(milliSeconds);
}

TropoCall.prototype.state = function () {
  return this._call_.getState();
}

TropoCall.prototype.isActive = function () {
  return this._call_.isActive();
}

TropoCall.prototype.redirect = function (too) {
  this._call_.redirect(too);
}

TropoCall.prototype.answer = function (timeout) { //in second
  if (!timeout || timeout == null) {
    timeout = 30;
  }
  this._call_.answer(timeout*1000);
}

TropoCall.prototype.startCallRecording = function (uri, format, key, keyUri) {
  if (!format || format == null) {
    format = 'audio/wav';
  }
  this._call_.startCallRecording(uri, format, key, keyUri);
}

TropoCall.prototype.stopCallRecording = function () {
  this._call_.stopCallRecording();
}

TropoCall.prototype.reject = function () {
  this._call_.reject();
  //document.writeln("reject() <br>");
}

TropoCall.prototype.hangup = function () {
  return this._call_.hangup();
  //document.writeln("hangup()<br>");
}

TropoCall.prototype.say = function (tts) {
  return this.prompt(tts);
}

TropoCall.prototype.ask = function (tts, options) {
  return this.prompt(tts, options);
}

TropoCall.prototype.record = function (tts, options){
	var oop = options
	if(oop!=null){ 
		oop['record'] = true
	}
	else{
	    oop = {repeat:1,record:true, beep:true, silenceTimeout:3, maxTime:30,timeout:30} 
    }
    return prompt(tts,oop)
}

TropoCall.prototype.transfer = function (too, options){
    var answerOnMedia = false
    var callerID = null
    var timeout = 30000
    var method = "bridged"
    var playrepeat = 1
    var playvalue = null
    var choices=null 
    
    var onSuccess=null
    var onError=null
    var onTimeout=null
    var onCallFailure=null
    var onChoice=null
    
    if(options != null){ 
      if(options["answerOnMedia"] != null) {
        answerOnMedia = options["answerOnMedia"]
      }
      if(options['callerID'] != null){
          callerID = options['callerID']
      } 
      if(options['callerId'] != null){
          callerID = options['callerId']
      } 
      if(options["timeout"] != null) {
          timeout = _parseTime(options["timeout"]); //convert float second to ms
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
    var event  = null

    try{
        var _ncall_ = new TropoCall(this._call_.transfer(too, callerID, answerOnMedia,timeout, playvalue, playrepeat, choices));
        event = new __voxeo__prompt__event("transfer", _ncall_)
        if(onSuccess != null){
            _handleCallBack(onSuccess,event)
        }
    } catch (e){
        if(e.message == "com.voxeo.tropo.ErrorException: Outbound call is timeout."){
            event = new __voxeo__prompt__event("timeout", null) // create event based on the timeout
            if(onTimeout != null){
                _handleCallBack(onTimeout,event) 
            }
        }
        else if(e.message == "com.voxeo.tropo.ErrorException: Outbound call can not complete."){
            event = new __voxeo__prompt__event("callfailure", null)
            if(onCallFailure != null){
                _handleCallBack(onCallFailure,event) 
            }
        }
        else if(e.message == "com.voxeo.tropo.ErrorException: Outbound call cancelled."){
            event = new __voxeo__prompt__event("choice", null)
            if(onChoice != null){
                _handleCallBack(onChoice,event) 
            }
        }
        else{ 
            //puts e.message
            log(e)
            event = new __voxeo__prompt__event("error", e.message)
            //TODO: ??? do we need to tell the callback what kind of error it is???
            if( onError != null){
                _handleCallBack(onError)
            }
            throw (e.message)
        }
    }
    return event
}

//if choice and record are on, return event.name = choice and event.value = grammar result and event.recordUI = recordURI
//if choice is on but not record, return event.name = choice, event.value = grammar result, and event.recordURI = null
//if record is on but not choice, return event.name = record, event.value = recordURI, and event.recordURI = recordURI
TropoCall.prototype.prompt = function (tts, options){
  var grammar = ""; // don't use null here, otherwise java object will get a string of "null" intead of a null object
  var timeout = 30000;  //milliseconds
  var onTimeout = null;
  var onChoices = null;
  var repeat = 0;
  var onError = null;
  var onEvent = null;
  var onHangup = null;
  var onBadChoice = null;
  var ttsOrUrl = "";
  var bargein = true;
  var choiceConfidence='0.3';
  var choiceMode='any'; // (dtmf|speech|any) - defaults to any
  
  //record parameters
  var record = false;
  var beep = true;
  var silenceTimeout = 5000;  // 5 seconds
  var maxTime = 30000; // 30 seconds

  var onSilenceTimeout = null;
  var onRecord = null;

  if(tts != null){//make sure the ttsOrUrl is at least an empty string before calling IncomingCall
    ttsOrUrl = tts;
  }

  if (options) {
    if(options["choices"]){ //make sure the grammar is at least an empty string before calling IncomingCall
      grammar = options["choices"]; 
    }
    onChoices = options["onChoice"];
    if(options["timeout"] != null) {
      timeout = _parseTime(options["timeout"]); //convert float second to ms
    }
    onTimeout = options["onTimeout"];
    if(options["repeat"] != null){
      repeat = Math.max(0, options["repeat"]);
    }
    onError = options["onError"];
    onEvent = options["onEvent"];
    onHangup = options["onHangup"];
    onBadChoice = options["onBadChoice"];
    if(options["bargein"] != null){
      bargein = options["bargein"]; 
    }
    if(options["choiceConfidence"] != null){
      choiceConfidence = options["choiceConfidence"]+""; 
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
      silenceTimeout = _parseTime(options["silenceTimeout"]); 
    }
    if(options["maxTime"] != null){
      maxTime = _parseTime(options["maxTime"]); 
    }
    onSilenceTimeout = options["onSilenceTimeout"];
    onRecord = options["onRecord"];
  }

  var event = null;

  for (var x=0; x<=repeat; x++){
    try{
      if(record){
        var result = this._call_.promptWithRecord(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout, record, beep, maxTime, silenceTimeout );
        event = new __voxeo__prompt__event("record", result.get('recordURL'), result.get('recordURL'));
        if (onRecord != null) {
          _handleCallBack(onRecord,event);
        }
        if( grammar != null && grammar != '' ){ // both choice and record are enabled
          var choice = new TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
          event = new __voxeo__prompt__event("choice", result.get('value'), result.get('recordURL'), choice);
          if (onChoices != null) {
            _handleCallBack(onChoices,event);
          }
        }
      }
      else{
        var result = this._call_.prompt(ttsOrUrl, bargein, grammar, choiceConfidence, choiceMode, timeout);
        var choice = new TropoChoice(result.get('concept'),result.get('interpretation'),result.get('confidence'),result.get('xml'),result.get('utterance'));
        event = new __voxeo__prompt__event("choice", result.get('value'), result.get('recordURL'), choice);
        if (onChoices != null) {
          _handleCallBack(onChoices,event);
        }
      }
      if (onEvent != null) {
        _handleCallBack(onEvent,event);
      }
      break;
    } catch (e){
    //print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"+e.message)
      if(e.message=="com.voxeo.tropo.ErrorException: NO_SPEECH" || e.message=="com.voxeo.tropo.FatalException: java.lang.RuntimeException: NoSpeech"){
        event = new __voxeo__prompt__event("timeout", null); // create event based on the timeout
        if(onTimeout != null){
          _handleCallBack(onTimeout,event);
        }
        if (onEvent != null) {
          _handleCallBack(onEvent,event);
        }
      }
      else if(e.message=="com.voxeo.tropo.ErrorException: NO_MATCH"){
        event = new __voxeo__prompt__event("badChoice", null);
        if (onBadChoice != null) {
          _handleCallBack(onBadChoice,event);
        }
        if (onEvent != null) {
          _handleCallBack(onEvent,event);
        }
      }
      else if(e.message=="java.lang.RuntimeException: silenseTime"){
        event = new __voxeo__prompt__event("silenceTimeout", null);
        if (onSilenceTimeout != null) {
          _handleCallBack(onSilenceTimeout);
        }
        if (onEvent != null) {
          _handleCallBack(onEvent,event);
        }
      }
      else if(e.message=="com.voxeo.tropo.FatalException: com.mot.mrcp.MrcpDisconnectedException: Disconnect"){
      event = new __voxeo__prompt__event("hangup", null);
        if(onHangup != null){
          _handleCallBack(onHangup,event);
        }
        if (onEvent != null) {
          _handleCallBack(onEvent,event);
        }
        break;
      }
      else {
        log(e);
        event = new __voxeo__prompt__event("error", e.message);
        //TODO: ??? do we need to tell the callback what kind of error it is???
        if(onError != null){
          _handleCallBack(onError);
        }
        if (onEvent != null) {
          _handleCallBack(onEvent,event);
        }
        throw (e.message)
      }
    }
  }
  return event;
}

////////////////// Global functions begin ///////////////////////

currentApp = new TropoApp(appInstance); //global app object, instance of TropoApp

var currentCall = null; //global call object, instance of TropoCall
if ( incomingCall != "nullCall" ) {
  currentCall = new TropoCall(incomingCall);
  currentCall.log("currentCall is assigned to incoming " + incomingCall.toString())
}

function call(too, options){
    var callerID = 'sip:Tropo@10.6.69.201'
    var answerOnMedia = false
    var timeout = 30000

    var onAnswer = null
    var onError = null
    var onTimeout = null
    var onCallFailure = null
	
	var recordUri = '';
	var recordFormat='audio/wav';

    if (options) {
        onAnswer = options['onAnswer']
        onError = options['onError']
        onTimeout = options['onTimeout']
        onCallFailure = options['onCallFailure']

        if(options["timeout"] != null) {
            timeout = _parseTime(options["timeout"]); //convert float second to ms
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
        if(options['recordUri'] != null){
            recordUri = options['recordUri']
        } 
        if(options['recordFormat'] != null){
            recordFormat = options['recordFormat']
        } 
    }

    var event  = null

    try{
        var _newCall_ = callFactory.call(callerID, too, answerOnMedia, timeout,recordUri,recordFormat)
        var _call_ = new TropoCall(_newCall_);
        if(currentCall == null){
            currentCall = _call_;
            currentCall.log("currentCall is assigned to outgoing " + _newCall_.toString())
        }
        event = new __voxeo__prompt__event("answer", _call_) 
        if(onAnswer != null){
            _handleCallBack(onAnswer,event)
        }
    } catch (e){
        if(e.message == "com.voxeo.tropo.ErrorException: Outbound call is timeout."){
            event = new __voxeo__prompt__event("timeout", null) // create event based on the timeout
            if(onTimeout != null){
                _handleCallBack(onTimeout,event) 
            }
        }
        else if(e.message == "com.voxeo.tropo.ErrorException: Outbound call can not complete."){
            event = new __voxeo__prompt__event("callfailure", null)
            if(onCallFailure != null){
                _handleCallBack(onCallFailure,event) 
            }
        }
        else{ 
            //puts e.message
            log(e)
            event = new __voxeo__prompt__event("error", e.message)
            //TODO: ??? do we need to tell the callback what kind of error it is???
            if( onError != null){
                _handleCallBack(onError)
            }
            throw (e.message)
        }
    }
    return event
}


function log(msg) {
  if(currentCall!=null && currentCall.isActive()){
    currentCall.log(msg);
  }
  else {
    appInstance.log(msg);
  }
}

function wait(milliSeconds) {
  if(currentCall!=null && currentCall.isActive()){
    currentCall.wait(milliSeconds);
  }
  else {
    appInstance.block(milliSeconds);
  }
}

function redirect(too) {
  currentCall.redirect(too);
}

function answer(timeout) {
  if(currentCall!=null){
    currentCall.answer(timeout);
  }
}

function startCallRecording(uri, format, key, keyUri) {
  if(currentCall!=null){
    currentCall.startCallRecording(uri, format, key, keyUri);
  }
}

function stopCallRecording() {
  if(currentCall!=null){
    currentCall.stopCallRecording();
  }
}

function reject() {
  currentCall.reject();
}

function hangup() {
  return currentCall.hangup();
}

function say(tts) {
  return currentCall.say(tts);
}

function ask(tts, options) {
  return currentCall.ask(tts, options);
}

function transfer(tts, options){
  return currentCall.transfer(tts, options);
}

function prompt(tts, options){
  return currentCall.prompt(tts, options);
}

function record(tts, options){
  return currentCall.record(tts, options);
}

////////////////////end shim of JavaScript//////////////////
