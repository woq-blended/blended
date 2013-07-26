'use strict';

var bundleApp = angular.module('bundleApp', ['bundleService'])

  .config(['$routeProvider', function($routeProvider) {

    $routeProvider
      .when('/bundles', {
        templateUrl: 'views/bundles.html',
        controller: 'bundleController'
      })
      .otherwise( { redirectTo: '/bundles' } );
  }]
);

