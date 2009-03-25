#require 'rubygems' # this will not be required in Ruby 1.9
#require 'active_support'
require 'tropo_common'
###################### Global functions begin ###########################

$currentApp = TropoApp.new($appInstance) #global app object, instance of TropoApp

$currentCall = nil # global call object, instance of TropoCall
if $incomingCall != "nullCall"
  $currentCall = TropoCall.new($incomingCall) 
  $currentCall.log("currentCall is assigned to incoming " + $incomingCall.toString())
end

######################### end shim of Ruby###########################
