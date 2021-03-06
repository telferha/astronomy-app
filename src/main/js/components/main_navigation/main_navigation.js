<<<<<<< HEAD:src/main/js/components/main_navigation/main_navigation.js
function Directive($state, appSettings, SessionService){
    "ngInject";

    function link(scope, element){

        var roles = SessionService.getUser().roles;
        if (roles.indexOf("USER") != -1){
            var role = "USER";
            scope.navigationLinks = appSettings[role]["mainNavigationLinks"];
        }


        scope.logout = function(){
            SessionService.destroy();
            $state.go('app.login');
        }

    }

    var directive = {
        restrict: 'E',
        templateUrl: 'components/main_navigation/main_navigation.html',
        link: link
    }

    return directive;
}

module.exports =  Directive;
=======
function Directive($state, appSettings, SessionService){
    "ngInject";

    function link(scope, element){

        var roles = SessionService.getUser().roles;
        if (roles.indexOf("USER") != -1){
            var role = "USER";
            scope.navigationLinks = appSettings[role]["mainNavigationLinks"];
        }


        scope.logout = function(){
            SessionService.destroy();
            $state.go('app.login');
            window.location.reload();
        }

    }

    var directive = {
        restrict: 'E',
        templateUrl: 'components/main_navigation/main_navigation.html',
        link: link
    }

    return directive;
}

module.exports =  Directive;
>>>>>>> origin/master:src/main/js/components/main_Navigation/main_navigation.js
