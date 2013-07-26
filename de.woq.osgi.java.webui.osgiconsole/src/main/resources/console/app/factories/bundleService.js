angular.module('bundleService', []).

  factory('Bundles', function() {

    var bundles = [
      { bundleId: 0, symbolicName: 'SystemBundle' }
    ];

    return {
      listBundles: function() {
        return bundles;
      }
    };
  }
);
