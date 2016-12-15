FROM atooni/blended-base:latest
MAINTAINER Blended Team version: 2.1.0-SNAPSHOT
ADD blended.demo.mgmt-2.1.0-SNAPSHOT-full-nojre.tar.gz /opt
RUN ln -s /opt/blended.demo.mgmt-2.1.0-SNAPSHOT /opt/mgmt
RUN chown -R blended.blended /opt/blended.demo.mgmt-2.1.0-SNAPSHOT
RUN chown -R blended.blended /opt/mgmt
USER blended
ENV JAVA_HOME /opt/java
ENV PATH /Users/andreas/.rvm/gems/ruby-2.1.7/bin:/Users/andreas/.rvm/gems/ruby-2.1.7@global/bin:/Users/andreas/.rvm/rubies/ruby-2.1.7/bin:/usr/local/heroku/bin:/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/bin:/opt/scala/bin:/opt/maven/bin:/Users/andreas/install/activator:/opt/sbt/bin:/Users/andreas/scripts:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/opt/X11/bin:/usr/local/git/bin:/usr/local/MacGPG2/bin:/Users/andreas/.rvm/bin:/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/bin
ENTRYPOINT ["/bin/sh", "/opt/mgmt/bin/blended.sh"]
EXPOSE 1099
EXPOSE 1883
EXPOSE 9191
