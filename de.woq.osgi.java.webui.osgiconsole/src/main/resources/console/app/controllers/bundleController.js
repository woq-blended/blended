bundleApp.controller('bundleController', function($scope, Bundles) {

  Bundles.async().then(function(data) {
    $scope.bundles = data.bundleInfo;
  });
});
