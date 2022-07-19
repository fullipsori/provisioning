import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class TCPSocketServer
{
  //initialize socket and input stream

  private Socket socket = null;
  private ServerSocket server = null;
  private DataInputStream in   = null;

  public  static String sleepMode = "NO";

  // constructor with port
  public TCPSocketServer(int port, String connMode)
  {

    int min = 10;
    int max = 100;
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

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

    String testData = "[TEST-START]--->"
              + " CREATE TABLE TB_CMCE_BRCE_CNTA_HUB_D_T( BRCE_HUB_ID NUMBER(19,0), CUST_NO NUMBER(15,0), ENTR_NO NUMBER(12,0),"
              + " IBM_SLTN_TRGET_CHNL_CD VARCHAR2(20),  -- 채널 코드 831120(쿠폰발급), 831130(배너), 831140(push), 831150(mms), 831160(쿠폰발급-iptv),"
              + " 831170(맞춤공지-iptv), 831180(mms-iptv) IBM_SLTN_MMST_MSTR_ID NUMBER(19,0), -- MMS BRCE_COIS_SET_ID NUMBER(19,0), --쿠폰발급 BRCE_PUSH_SET_ID"
              + " NUMBER(19,0), --PUSH BRCE_BNNR_SET_ID NUMBER(19,0), --배너 BRCE_PSND_ANNC_SET_ID NUMBER(19,0), --맞춤공지 BRCE_MFA_SET_ID NUMBER(19,0),"
              + " --월정액 BRCE_RTM_SNRO_ID VARCHAR2(20), --시나리오 ID BRCE_RTM_TRGET_KND_CD VARCHAR2(20), --선타겟팅/실시간타겟팅 CONSTRAINT TB_CMCE_BRCE_"
              + "CNTA_HUB_D_T_pk PRIMARY KEY (BRCE_HUB_ID)); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, "
              + "IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, "
              + "BRCE_RTM_TRGET_KND_CD) VALUES(0, 1, 1, '831160', NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID,"
              + "CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID,"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(1, 2, 2, '831160', NULL, 2, NULL, NULL, NULL, NULL, NULL, NULL);"
              + " INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID,"
              + " BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(2, 3, 3,"
              + " '831180', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO,"
              + " IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID,"
              + " BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(3, 4, 4, '831140', NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL); INSERT INTO"
              + " TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID,"
              + " BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(4, 5, 5, '831130', NULL, NULL,"
              + " NULL, 1, NULL, NULL, NULL, NULL); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST"
              + "_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD)"
              + " VALUES(5, 6, 6, '831170', NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO,"
              + " IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID,"
              + " BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(6, 7, 7, '831100', NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL); INSERT INTO TB_CMCE_BRCE_CNTA_"
              + "HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID,"
              + " BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(7, 8, 8, '831160', 3, NULL, NULL, NULL, NULL, NULL,"
              + " NULL, '선타겟팅'); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO, ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID,"
              + " BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID, BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD)"
              + " VALUES(8, 9, 9, '831140', NULL, NULL, 1, NULL, NULL, NULL, NULL, '실시간타겟팅'); INSERT INTO TB_CMCE_BRCE_CNTA_HUB_D_T(BRCE_HUB_ID, CUST_NO,"
              + " ENTR_NO, IBM_SLTN_TRGET_CHNL_CD, IBM_SLTN_MMST_MSTR_ID, BRCE_COIS_SET_ID, BRCE_PUSH_SET_ID, BRCE_BNNR_SET_ID, BRCE_PSND_ANNC_SET_ID,"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + " BRCE_MFA_SET_ID, BRCE_RTM_SNRO_ID, BRCE_RTM_TRGET_KND_CD) VALUES(9, 10, 10, '831130', NULL, NULL, NULL, 1, NULL, NULL, NULL, '실시간타겟팅')"
              + "<----[TEST-END]";

    while(true) {
        try
        {
          System.out.println("클라이언트 접수 요청을 ["+port+"] 포트로 받을 준비가 되었습니다.");
          socket = server.accept();
          System.out.println("Client accepted - 수락하였습니다.["+port+"]");

          // takes input from the client socket
          OutputStream os = socket.getOutputStream();
          DataOutputStream ds = new DataOutputStream(os);

          String message = null;
          String str= "";

          // reads message from client until "Over" is sent
          BufferedReader br = null;
          InputStream inputStream = null;;
          boolean textMode = (connMode.equalsIgnoreCase("TEXT"))? true : false;

          while (true) {
            try
            {
                System.out.println("read blocking");
                int BUF_SIZE = 1024*7;

                if(textMode) {
                  br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf8"), BUF_SIZE);

                  //클라이언트에서 보낸 문자열 출력
                  StringBuffer buffer = new StringBuffer();
                  while(true) {
                    int ch = br.read();
                    if((ch<0) || (ch == '\n')) {
                      break;
                    }
                    buffer.append((char) ch);
                  }
                  str = buffer.toString() + String.format(" From Server [%d]",port);

                }else{
                  if(inputStream == null) {
                    inputStream = socket.getInputStream();
                  }
                  byte[] recvBuffer = new byte[BUF_SIZE];
                  int nReadSize = inputStream.read(recvBuffer);
                  System.out.printf("read return:%d\n", nReadSize);

                  if(nReadSize <= 0){
                      break;
                  }
                  str = new String("response:") + String.format(" From Server(%d) [%d] time:%s", nReadSize, port, dtf.format(LocalDateTime.now()));
                }


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

                if(textMode) {
                  message += "\n";
                  ds.writeUTF(message);
                  System.out.println("["+random_int+" msecs][데이터 보내기 성공:"+message.length()+"]");
                }else{
                  // ds.write(message.getBytes());
                  ds.write(testData.getBytes("UTF-8"));
                  System.out.println("["+random_int+" msecs][데이터 보내기 성공:"+testData.length()+"]");
                }

            } catch(IOException i) {
              System.out.println(i);
              break;
            }
          }

          System.out.println("Closing connection");

          // close connection
          if(br != null) {
            br.close();
          }
          if(inputStream != null) {
            inputStream.close();
          }
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
    String connMode = "TEXT";

    if(args.length < 2) {
      System.out.println("사용법:  java TCPServer [MODE] [PortNo] [SleepMode]");
      System.out.println("  - 랜덤하게 응답을 지연시켜 보내고 싶을때 sleep mode => YES 로 지정한다.");
      System.out.println("    기본 sleep mode는 NO");
      System.out.println("    java TCPServer 9001 YES");
      System.out.println("");
      return;
    } else if(args.length > 1) {
      connMode = args[0];
      portNo = Integer.parseInt(args[1]);
      System.out.println("connMode:" + connMode + " portNo:" + portNo);

      if(args.length > 2) {
        sleepMode = args[1];
        System.out.println("슬립모드 시작여부 ["+sleepMode+"]");
      }
    }


    TCPSocketServer server = new TCPSocketServer(portNo, connMode);
  }
}