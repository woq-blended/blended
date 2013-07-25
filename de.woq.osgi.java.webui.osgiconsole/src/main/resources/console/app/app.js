var bundleApp = angular.module('bundleApp', []);

bundleApp.controller('BundleController', function($scope, bundleFactory) {

  init();

  function init() {
    $scope.bundles = bundleFactory.getBundles();
  }
});

bundleApp.factory('bundleFactory', function() {

  var factory = {};

  var bundles = [
    { bundleId: 0, symbolicName: 'SystemBundle' }
  ];

  factory.getBundles = function() {
    return bundles;
  };

  return factory;
});
