var FormComponent = ng.core.Component({
	selector : "search-form",
	directives : [ ng.router.ROUTER_DIRECTIVES ],
	templateUrl : "componentTemplates/search-form.html",
}).Class({
	constructor : [ ng.router.RouteParams, 
					function(params) {
						this.searchParams = {
							query : params.get("query")
						};
					} ],
	ngOnInit : function() {
		this.keyup = function(e) {
			this.searchParams = {
				query : e.srcElement.value
			};
		};
	}
})

var SearchResultComponent = ng.core.Component({
	selector : "search-result",
	directives : [ FormComponent ],
	viewProviders : [ ng.http.HTTP_PROVIDERS ],
	templateUrl : "componentTemplates/searchResult.html"
}).Class(
		{
			constructor : [ ng.router.RouteParams, ng.http.Http,
					function(params, http) {
						this.params = params;
						this.http = http;
						this.response = [];
					} ],
			ngOnInit : function() {
				var q = this.params.get("query");
				this.http.get("getData/" + q).subscribe(function(res) {
					this.response = JSON.parse(res._body);
				}.bind(this));
			}
		})

var NotFoundComponent = ng.core.Component({
	selector : "name-search",
	templateUrl : "componentTemplates/notFound.html"
}).Class({
	constructor : function() {
	}
})

var AppComponent = ng.core.Component({
	selector : "app",
	directives : [ ng.router.ROUTER_DIRECTIVES ],
	templateUrl : "componentTemplates/app.html"
}).Class({
	constructor : function() {
	}
})

AppComponent = ng.router.RouteConfig([ {
	path : "/",
	component : SearchResultComponent,
	name : "Home"
}, {
	path : "/search-result",
	component : SearchResultComponent,
	name : "SearchResult"
}, {
	path : "/*path",
	component : NotFoundComponent,
	name : "NotFound"
} ])(AppComponent);

ng.core.enableProdMode();

ng.platform.browser.bootstrap(AppComponent, [ ng.router.ROUTER_PROVIDERS,
		ng.core.provide(ng.router.APP_BASE_HREF, {
			useValue : "/"
		}) ]);