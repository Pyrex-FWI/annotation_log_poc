vcl 4.0;

###################
# VMOD inclusions #
###################

# Documentation Link to add specific stuff
# https://wiki.linkbynet.com/softs:varnish

# Uncomment the following in case of using directors ( load-balancing )
#import directors;
#
# Uncomment the following in case of using std functions ( syslog ... )
#import std;

# VMOD ending #

######################
# BACKEND definition #
######################

backend WEB01 {
 .host = "127.0.0.1";
 .port = "80";
 .connect_timeout       = 2s;
 .first_byte_timeout    = 60s;
 .between_bytes_timeout = 2s;
}


#backend WEB02 {
# .host = "x.x.x.x";
# .port = "80";
# .connect_timeout       = 2s;
# .first_byte_timeout    = 60s;
# .between_bytes_timeout = 2s;
#}

# BACKEND definition ending #


#############################
# LOAD BALANCING DEFINITION #
#############################

sub vcl_init {


    #Uncomment the following in case of using round robin director
    #new rr = directors.round_robin();
    #rr.add_backend(WEB01);
    #rr.add_backend(WEB02);

    return (ok);
}


############
# RECV SUB #
############

sub vcl_recv {

    # Setting the backend
    set req.backend_hint = WEB01;

    if (req.http.Authorization || req.http.Authenticate || req.http.Cookie) {
        /* Not cacheable by default */
        return (pass);
    }

    #Uncomment the following in case of using round robin director
    #set req.backend_hint = rr.backend();

    # do not proceed varnish stuff on unknown http methods
    if (req.method != "GET" &&
      req.method != "HEAD" &&
      req.method != "PUT" &&
      req.method != "POST" &&
      req.method != "TRACE" &&
      req.method != "OPTIONS" &&
      req.method != "DELETE") {
        /* Non-RFC2616 or CONNECT which is weird. */
        return (pipe);
    }

    # do not proceed varnish stuff on post or put methods
    if ((req.method == "POST" || req.method == "PUT") &&
      req.http.transfer-encoding ~ "chunked") {
        return(pipe);
    }

    if (req.method != "GET" && req.method != "HEAD") {
        /* We only deal with GET and HEAD by default */
        return (pass);
    }

    # X-Forwarded-Proto
    if (req.http.X-Forwarded-Proto == "https"){
        set req.http.X-Forwarded-Proto = "https";
    }

    # Aucune mise en cache sur les appels Ã  lbn_admin et lbn_maintenance
    if (req.url ~ "^/lbn_(admin|maintenance)/"){
        return (pass);
    }

    # Aucune mise en cache sur les appels Ã  lbn_admin et lbn_maintenance
    if (req.http.host ~ "intranet.int.suez.lbn.fr"){
        return (pass);
    }


    # We specify the TTL for some media - and force lookup into cache
    if (req.url ~ "\.(aif|aiff|au|avi|bin|bmp|cab|carb|cct|cdf|class|css)$"  ||
        req.url ~ "\.(dcr|doc|dtd|eps|exe|flv|gcf|gff|gif|grv|hdml|hqx)$"    ||
        req.url ~ "\.(ico|ini|jpeg|jpg|js|mov|mp3|nc|pct|png|ppc|pws)$"      ||
        req.url ~ "\.(swa|swf|tif|ttf|txt|vbs|w32|wav|wbmp|wml|wmlc|wmls|wmlsc)$"||
        req.url ~ "\.(xml|xsd|xsl|zip|woff|woff2|eot)$") {
        unset req.http.Cookie;
        return (hash);
    }

    if (req.http.Authorization || req.http.Authenticate || req.http.Cookie) {
        /* Not cacheable by default */
        return (pass);
    }

    return (hash);
}


#############
## PIPE SUB #
#############

#############
## PIPE SUB #
#############


sub vcl_pipe {
    return (pipe);
}

############
# PASS SUB #
############
#
sub vcl_pass {
    return (fetch);
}

############
# HASH SUB #
############

sub vcl_hash {
    hash_data(req.url);
    if (req.http.host) {
        hash_data(req.http.host);
    } else {
        hash_data(server.ip);
    }
    return (lookup);
}

#############
# PURGE SUB #
#############
#
sub vcl_purge {
    return (synth(200, "Purged"));
}

###########
# HIT SUB #
###########

sub vcl_hit {
    # return an object in cache
    if (obj.ttl >= 0s) {
        return (deliver);
    }
    # return an object in grace
    if (obj.ttl + obj.grace > 0s) {
        return (deliver);
    }
    # looking for caching and object, then deliver
    return (miss);
}

############
# MISS SUB #
############
#
sub vcl_miss {
    return (fetch);
}

###############
# DELIVER SUB #
###############
#
sub vcl_deliver {
    # Adding Hit/Miss header
    if (obj.hits > 0) {
            set resp.http.X-Cache = "HIT";
    } else {
            set resp.http.X-Cache = "MISS";
    }
    # Removing some header
    #unset resp.http.via;
    #unset resp.http.X-Varnish;
    #unset resp.http.Varnish-Control;
    #unset resp.http.Varnish-Public;
    #unset resp.http.Served-by;
    #unset resp.http.X-Powered-By;

    return (deliver);
}

############
# SYNT SUB #
############

sub vcl_synth {
    set resp.http.Content-Type = "text/html; charset=utf-8";
    set resp.http.Retry-After = "5";
    synthetic( {"
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
 "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
  <head>
    <title>"} + resp.status + " " + resp.reason + {"</title>
  </head>
  <body>
    <h1>Error "} + resp.status + " " + resp.reason + {"</h1>
    <p>"} + resp.reason + {"</p>
    <h3>int:</h3>
    <p>XID: "} + req.xid + {"</p>
    <hr>
    <p>Varnish cache server</p>
  </body>
</html>
"} );
    return (deliver);
}


#####################
# BACKEND FETCH SUB #
#####################

sub vcl_backend_fetch {
    return (fetch);
}

########################
# BACKEND RESPONSE SUB #
########################

sub vcl_backend_response {
    # We specify the TTL for some media, additionaly, we unset no needed headers if present
    if (bereq.url ~ "\.(aif|aiff|au|avi|bin|bmp|cab|carb|cct|cdf|class|css)$"  ||
        bereq.url ~ "\.(dcr|doc|dtd|eps|exe|flv|gcf|gff|gif|grv|hdml|hqx)$"    ||
        (bereq.url ~ "\.(ico|ini|jpeg|jpg|js|mov|mp3|nc|pct|png|ppc|pws)$" && (!bereq.url ~ "captcha-default\.png$"))      ||
        bereq.url ~ "\.(swa|swf|tif|ttf|txt|vbs|w32|wav|wbmp|wml|wmlc|wmls|wmlsc)$"||
        bereq.url ~ "\.(xml|xsd|xsl|zip|woff|woff2|eot)$") {
        unset beresp.http.Etag;
        unset beresp.http.Set-Cookie;
        unset beresp.http.Pragma;
        unset beresp.http.Expires;
        unset beresp.http.Cache-Control;
        unset beresp.http.Server;
        set beresp.grace = 1h;
        set beresp.ttl = 1d;
        set beresp.http.X-TTL = "86400";
        set beresp.http.Cache-Control = "public, max-age=3600";
        return (deliver);
    }

    if (beresp.ttl <= 0s ||
      beresp.http.Set-Cookie ||
      beresp.http.Surrogate-control ~ "no-store" ||
      (!beresp.http.Surrogate-Control &&
        beresp.http.Cache-Control ~ "no-cache|no-store|private") ||
      beresp.http.Vary == "*") {
        # do not cache and remember this for 10 min
        set beresp.ttl = 600s;
        set beresp.uncacheable = true;
    }

        unset beresp.http.Server;
        return (deliver);
}

######################
## BACKEND ERROR SUB #
######################

sub vcl_backend_error {
    set beresp.http.Content-Type = "text/html; charset=utf-8";
    set beresp.http.Retry-After = "5";
    synthetic( {"
<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
 "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
  <head>
    <title>"} + beresp.status + " " + beresp.reason + {"</title>
  </head>
  <body>
    <h1>Error "} + beresp.status + " " + beresp.reason + {"</h1>
    <p>"} + beresp.reason + {"</p>
    <h3>int</h3>
    <p>XID: "} + bereq.xid + {"</p>
    <hr>
    <p>Varnish cache server</p>
  </body>
</html>
"} );
    return (deliver);
}

############
# FINI SUB #
############

sub vcl_fini {
    return (ok);

