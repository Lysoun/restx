'use strict';

angular.module('admin').factory('baseUri', function() {
   return document.location.href.replace(/^https?:\/\/[^\/]+\//, '/').replace(/\/@.*/, '');
});

angular.module('admin').controller('LoginController', function($scope, baseUri, $http) {
    $scope.authenticate = function() {
        $http.post(baseUri + '/sessions', {principal: {name: $scope.username, passwordHash: SparkMD5.hash($scope.password)}})
            .then(function onSuccess(response) {
                console.log('authenticated', response.data, response.status);
                window.location = $.querystring('backTo') || (baseUri + '/@/ui/');
            }, function onError(response) {
                console.log('error', response.data, response.status);
                alertify.success("Authentication error, please try again.");
            });
    }
});

angular.module('admin').directive('formAutofillFix', function($timeout) {
    return function(scope, elem, attrs) {
        // Fix autofill issues where Angular doesn't know about autofilled inputs
        if(attrs.ngSubmit) {
            $timeout(function() {
                elem.unbind('submit').submit(function(e) {
                    e.preventDefault();
                    elem.find('input, textarea, select').trigger('input').trigger('change').trigger('keydown');
                    scope.$apply(attrs.ngSubmit);
                });
            });
        }
    };
});
