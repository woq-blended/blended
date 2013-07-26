bundleApp.controller('bundleController', function($scope, Bundles) {

  $scope.bundles = Bundles.listBundles();
});