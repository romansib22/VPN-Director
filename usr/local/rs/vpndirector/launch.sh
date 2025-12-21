#!/bin/sh
path="/usr/local/rs"
name="vpndirector"
cd $path'/'$name
/usr/bin/java \
-server \
-Duser.timezone=GMT -Duser.language=en \
-Xms128m -Xmx256m -Xss1m \
-Dspring.config.additional-location=file:${path}/${name}/ \
-jar ${path}/${name}/vpndirector.jar \
1> ${path}/${name}/log/${name}.log 2>&1 &
