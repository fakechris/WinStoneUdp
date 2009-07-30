@echo off

set JAVA_HOME=C:\Java\jdk1.6.0_06
set WINSTONE_HOME=D:\java\WinStoneUdp
set CATALINA_HOME=C:\Java\jakarta-tomcat-5.0.30
set JAVA_OPTS=-Djava.endorsed.dirs=%JAVA_HOME%\jre\lib\ext

set CP=.
set CP=%CP%;%WINSTONE_HOME%\target\winstone-0.9.10.jar
@rem set WINSTONE_OPTS=--prefix=/examples 
set WINSTONE_OPTS=
@rem set WINSTONE_OPTS=%WINSTONE_OPTS% --webroot=%CATALINA_HOME%\webapps\jsp-examples
set WINSTONE_OPTS=%WINSTONE_OPTS% --warfile=D:\java\Quash-1.0-rc2\dist\Quash.war
set WINSTONE_OPTS=%WINSTONE_OPTS% --debug=7
set WINSTONE_OPTS=%WINSTONE_OPTS% --commonLibFolder=c:\java\lib
set WINSTONE_OPTS=%WINSTONE_OPTS% --javaHome=%JAVA_HOME%
set WINSTONE_OPTS=%WINSTONE_OPTS% --toolsJar=%JAVA_HOME%\lib\tools.jar
@rem set WINSTONE_OPTS=%WINSTONE_OPTS% --argumentsRealm.passwd.rickk=rickk --argumentsRealm.roles.rickk=test,tomcat

@rem ********************************************************************
@rem            Uncomment for non-1.4 jdks
@rem ********************************************************************
@rem set CP=%CP%;%WINSTONE_HOME%\build\lib\gnujaxp.jar
@rem set CP=%CP%;%WINSTONE_HOME%\dist\xml-apis.jar
@rem set CP=%CP%;%WINSTONE_HOME%\dist\xercesImpl.jar

@rem ********************************************************************
@rem            Uncomment for jsp support
@rem ********************************************************************
set CP=%CP%;%CATALINA_HOME%\common\lib\jsp-api.jar
set CP=%CP%;%CATALINA_HOME%\common\lib\jasper-runtime.jar
set CP=%CP%;%CATALINA_HOME%\common\lib\jasper-compiler.jar
set CP=%CP%;%CATALINA_HOME%\common\lib\commons-logging-api.jar
set CP=%CP%;%CATALINA_HOME%\common\lib\commons-el.jar
set CP=%CP%;%CATALINA_HOME%\common\lib\ant.jar
set CP=%CP%;%JAVA_HOME%\lib\tools.jar
set WINSTONE_OPTS=%WINSTONE_OPTS% --useJasper

@rem ********************************************************************
@rem            Uncomment for invoker support (ie Tomcat style)
@rem ********************************************************************
set WINSTONE_OPTS=%WINSTONE_OPTS% --useInvoker

@rem set CP=%CP%;c:\java\jars\activation.jar
@rem set CP=%CP%;c:\java\jars\mail.jar

echo Class Paths: %CP%
echo Options: %WINSTONE_OPTS%

%JAVA_HOME%\bin\java -server -cp %CP% %JAVA_OPTS% winstone.Launcher %WINSTONE_OPTS%