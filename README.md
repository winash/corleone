# Corleone
it gives you translations… and maybe someday you have opportunity to repay us the favour, capisce?


## Security

Corleone provides a basic implementation of the OAUTH2 [Authorization Code Grant flow]( http://tools.ietf.org/html/rfc6749#section-4.1.3).

The mechanism can be easily enabled / disabled with 

    oauth2.enabled = true / false

The mechanism can be configured as follows:
    
    # callback to which the user is redirected by the authentication server
    oauth2.callback.url = "https://localhost:9443/oauth_callback"
  
    # access token endpoint
    oauth2.access.token.url = "https://auth.server.com/oauth2/access_token"

    # authorization server endpoint
    oauth2.authorization.url = "https://auth.server.com/z/oauth2/authorize"

    # token info endpoint
    oauth2.token.info.url = "https://auth.server.com/oauth2/tokeninfo"

    # file containing the OAUTH2 credentials
    # NOTE: content must follow the format "{"client_id":"<client id>","client_secret":"<client secret>"}"
    oauth2.credentials.filePath= "/tmp/credentials/client.json"

    # timout in ms for the requests against OAUTH infrastructure
    oauth2.request.timeout = 5000

    # specifies the expiry time boundary in seconds after which the OAUTH2 mechanism should try to reresh the access token
    oauth2.token.refresh.expiry.limit = 300

    # time in seconds after which the credentials cache should expire automatically
    oauth2.credentials.cache.expiry.time = 300

    # additional server paths excluded from the OAUTH2 validation
    oauth2.excluded.paths = ["/webjars", "/assets"]




## License

Copyright [2015] Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

