angular.module('bundleService', []).

  factory('Bundles', function($http) {

    return {
      async: function() {
        var promise = $http.get('http://localhost:8080/osgiManagement/bundles').then(function (response) {
          // The then function here is an opportunity to modify the response
          console.log(response);
          // The return value gets picked up by the then in the controller.
          return response.data;
        });
        // Return the promise to the controller
        return promise;
      }
    }
  }
);

