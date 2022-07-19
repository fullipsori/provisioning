[TPS Generator 사용법]

java -jar TPSGen.jar -help 를 치시면 옵션이 나옵니다. 
사용이 예상이 되는 옵션에 대해서 설명합니다.

 - file : 로드테스트를 위해 읽어들이는 파일 패스입니다.
          파일 라인 단위로 읽어서 보내게 되고 time/count 값에 따라서 file 의 목록을 반복해서 보내게 됩니다.
 - server : 접속할 서버로서 형태는 "tcp://host:port" 로 주시면 됩니다. 아무것도 않주면 localhost:7222입니다.
 - queue : 메세지를 보낼려고 하는 ems queue 정보입니다.
 - time : 수행 시간(초) 위주로 동작하실꺼면 count=0 으로 주시기 바랍니다. 그렇지 않으면 count 값이 먼저 적용됩니다.
 - count : 최대 전송 횟수
 - rate : tps 입니다.  1000 인경우 1000tps 입니다.  
 
 [count=0 이고 time=0 이면 file 목록만 전송하고 끝남]
 
 예제 : server(localhost)의 port(7222) 의 ems queue에 file.txt 를 읽어서 20초 동안 vms-q 에 4000 tps 로 전송
 java -jar TPSGen.jar -file "file.txt" -time 20 -count 0 -queue "vms-q" -rate 4000
 
 
 
 