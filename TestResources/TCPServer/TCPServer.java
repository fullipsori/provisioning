import java.net.*;
import java.io.*;
import java.lang.Thread;


public class TCPServer
{
  //initialize socket and input stream

  private Socket socket = null;
  private ServerSocket server = null;
  private DataInputStream in   = null;

  public  static String sleepMode = "NO";

  // constructor with port
  public TCPServer(int port)
  {

    int min = 10;
    int max = 100;

    //Generate random int value from 50 to 100
    System.out.println("Random value in int from "+min+" to "+max+ ":");


    // starts server and waits for a connection
    try{
      server = new ServerSocket(port);
      System.out.println("Server started [Sleep Mode: "+sleepMode+"]");
      System.out.println("Waiting for a client ... 접속 대기 중입니다.");
    }catch(Exception i) {
      System.out.println("서버 소켓 생성 중 오류 발생: "+ i);
      return;
    }

    while(true) {
        try
        {
          System.out.println("클라이언트 접수 요청을 ["+port+"] 포트로 받을 준비가 되었습니다.");
          socket = server.accept();
          System.out.println("Client accepted - 수락하였습니다.["+port+"]");

          // takes input from the client socket
          OutputStream os = socket.getOutputStream();
          DataOutputStream ds = new DataOutputStream(os);
          BufferedReader br = null;

          byte[] bytes = null;
          String message = null;
          String str= "";

          // reads message from client until "Over" is sent
          while (true) {
            try
            {
                int BUF_SIZE = 1024*7;
                br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf8"), BUF_SIZE);

                // 클라이언트에서 보낸 문자열 출력
                StringBuffer buffer = new StringBuffer();
                while(true) {
                  int ch = br.read();
                  if((ch<0) || (ch == '\n')) {
                    break;
                  }
                  buffer.append((char) ch);
                }

                str = buffer.toString() + String.format(" From Server [%d]",port);
                message = str;

                if(str.contains("quit")) {
                  System.out.println("서버를 종료하겠습니다.!! 수고하셨습니다.");
                  return;
                }

                int random_int = 0;

                if(sleepMode.contains("YES")) {
                  random_int = (int)Math.floor(Math.random()*(max-min+1)+min);
                  message += " 응답지연 ("+random_int+")";
                  try {
                    Thread.sleep(random_int);
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                }

                message += "\n";
                ds.writeUTF(message);
                System.out.println("["+random_int+" msecs][데이터 보내기 성공:"+str+"]");

            } catch(IOException i) {
              System.out.println(i);
              break;
            }
          }

          System.out.println("Closing connection");

          // close connection
          br.close();
          socket.close();

        } catch(IOException i) {
          System.out.println(i);
          break;
        }

        System.out.println("다시 요청을 받을 준비를 하겠습니다.");
    }

  }

  public static void main(String args[])
  {
    int portNo = 5678;

    if(args.length < 1) {
      System.out.println("사용법:  java TCPServer [PortNo] [SleepMode]");
      System.out.println("  - 랜덤하게 응답을 지연시켜 보내고 싶을때 sleep mode => YES 로 지정한다.");
      System.out.println("    기본 sleep mode는 NO");
      System.out.println("    java TCPServer 9001 YES");
      System.out.println("");
      return;
    } else if(args.length > 0) {
      portNo = Integer.parseInt(args[0]);
      if(args.length > 1) {
        sleepMode = args[1];
        System.out.println("슬립모드 시작여부 ["+sleepMode+"]");
      }
    }


    TCPServer server = new TCPServer(portNo);
  }
}