package com.dropwave.tv;

import java.io.*;
import java.net.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.zip.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Dependency-free HTTP/1.1 LAN server. File contents are streamed; never held in RAM. */
public final class LanServer implements AutoCloseable {
    public interface Repository {
        List<Entry> list() throws Exception;
        Entry upload(String filename, InputStream input, long length) throws Exception;
        Opened open(String id) throws Exception;
        void remove(String id) throws Exception;
        default Entry uploadTo(String name,String folder,InputStream in,long length) throws Exception {
            if(folder!=null&&!folder.isEmpty())throw new UnsupportedOperationException("Folder uploads unsupported on this device");
            return upload(name,in,length);
        }
        default long freeBytes(){return -1;}
        default void mkdir(String folder) throws Exception {throw new UnsupportedOperationException("Folders unsupported");}
        default void rename(String id,String name) throws Exception {throw new UnsupportedOperationException("Rename unsupported");}
        default void move(String id,String folder) throws Exception {throw new UnsupportedOperationException("Move unsupported");}
        default Entry copy(String id,String folder) throws Exception {throw new UnsupportedOperationException("Copy unsupported");}
        default void removeFolder(String folder) throws Exception {throw new UnsupportedOperationException("Delete folder unsupported");}
        default List<String> folders() throws Exception {return Collections.emptyList();}

    }
    public interface HomePage { InputStream open() throws IOException; }
    public static final class Entry {
        public final String id, name, mime, source;
        public final long size;
        public final String folder;
        public Entry(String id,String name,long size,String mime,String source) {
            this(id,name,size,mime,source,"");
        }
        public Entry(String id,String name,long size,String mime,String source,String folder) {
            this.id=id;this.name=name;this.size=size;this.mime=mime;this.source=source;this.folder=folder==null?"":folder;
        }
    }
    public static final class Opened implements AutoCloseable {
        public final InputStream stream;
        public final long length;
        public final String name,mime;
        public Opened(InputStream stream,long length,String name,String mime) {
            this.stream=stream;this.length=length;this.name=name;this.mime=mime;
        }
        public void close() throws IOException { stream.close(); }
    }
    private final Repository repository;
    private final HomePage homePage;
    private final SecureRandom random=new SecureRandom();
    private final ResumeStore resume;
    private final TransferJournal journal;
    private final TrustRegistry trust;
    private final String davPassword;
    private volatile String clipboard="";
    private volatile long clipboardTime=0;
    private final Map<String,Pending> pending =new ConcurrentHashMap<>();
    private final Set<String> pendingRemember=ConcurrentHashMap.newKeySet();
    private volatile boolean requireApproval=false;
    private volatile boolean remoteEnabled=false;
    private volatile RemoteCommand remoteCommand;
    public interface RemoteCommand {void accept(String action);}
    public void setRemoteControl(boolean enabled,RemoteCommand consumer){remoteEnabled=enabled;remoteCommand=consumer;}
    public boolean remoteEnabled(){return remoteEnabled;}

    private static class Pending {String id,name,session;long expires;volatile String state="waiting";Pending(String i,String n,String s){id=i;name=n;session=s;expires=System.currentTimeMillis()+120000;}}


    private final Map<String,Long> sessions=new ConcurrentHashMap<>();
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>();
    private final ExecutorService workers=new ThreadPoolExecutor(5,5,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(25));
    private final AtomicLong transferred=new AtomicLong();
    private final AtomicInteger active=new AtomicInteger();
    private volatile boolean running;
    private ServerSocket listener;
    private volatile boolean tlsEnabled=false;
    private Thread acceptor;
    private UdpDiscovery discovery;
    private String pin;
    private static final long MAX_FILE=1_099_511_627_776L; // 1 TiB; actual storage may be smaller
    private static final long SESSION_MS=12L*60*60*1000;
    private static final class Attempt { int failures; long blockedUntil; }

    public LanServer(Repository repository,HomePage homePage){this(repository,homePage,new File(System.getProperty("java.io.tmpdir"),"dropwave-server"));}
    public LanServer(Repository repository,HomePage homePage,File workDir){
        this.repository=repository;this.homePage=homePage;
        try {if(!workDir.exists())workDir.mkdirs();resume=new ResumeStore(new File(workDir,"resumable"));journal=new TransferJournal(new File(workDir,"history.tsv"));trust=new TrustRegistry(new File(workDir,"trusted.txt"));
            File passFile=new File(workDir,"dav-secret.txt");
            if(!passFile.isFile()){
                byte[] pwd=new byte[24];random.nextBytes(pwd);
                java.nio.file.Files.write(passFile.toPath(),Base64.getUrlEncoder().withoutPadding().encode(pwd),java.nio.file.StandardOpenOption.CREATE_NEW);
                try{java.nio.file.Files.setPosixFilePermissions(passFile.toPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}
                catch(UnsupportedOperationException ignored){}
            }
            davPassword=new String(java.nio.file.Files.readAllBytes(passFile.toPath()),StandardCharsets.US_ASCII).trim();}
        catch(IOException e){throw new IllegalStateException("Cannot initialize transfer store",e);}
    }
    /** Shown only on the server device. Never exported through the browser API. */
    public String webdavPassword(){return davPassword;}
    public boolean requireApproval(){return requireApproval;}
    public void setRequireApproval(boolean enabled){requireApproval=enabled;}
    public List<String[]> pendingRequests(){List<String[]> result=new ArrayList<>();for(Pending p:pending.values())if("waiting".equals(p.state)&&p.expires>System.currentTimeMillis())result.add(new String[]{p.id,p.name});return result;}
    public void decide(String id,boolean approve){Pending p=pending.get(id);if(p!=null)p.state=approve?"approved":"denied";}
    public void disconnectAll(){sessions.clear();trust.revokeAll();pending.clear();}
    public String devices(){return trust.json();}
    public void revokeDevice(String hash){trust.revoke(hash);for(String sid:sessions.keySet())if(TrustRegistry.hash(sid).equals(hash))sessions.remove(sid);}

    public synchronized int start(int preferredPort) throws IOException { return startWithTLS(preferredPort,null); }
    /** TLS is optional: browsers must trust the local certificate themselves. */
    public synchronized int startSecure(int preferredPort,SSLContext ssl) throws IOException {
        if(ssl==null)throw new IllegalArgumentException("TLS context required");
        return startWithTLS(preferredPort,ssl);
    }
    private synchronized int startWithTLS(int preferredPort,SSLContext ssl) throws IOException {
        if(running)return listener.getLocalPort();
        IOException last=null;
        for(int i=0;i<5;i++) {
            try {
                listener=ssl==null?new ServerSocket():(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket();
                listener.setReuseAddress(true);
                if(listener instanceof SSLServerSocket){
                    SSLServerSocket ss=(SSLServerSocket)listener;
                    ss.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
                }
                listener.bind(new InetSocketAddress("0.0.0.0",preferredPort+i),50);
                tlsEnabled=ssl!=null;break;
            }
            catch(IOException e) { last=e; if(listener!=null)try{listener.close();}catch(IOException ignored){} listener=null; }
        }
        if(listener==null)throw last;
        pin=String.format(Locale.US,"%06d",random.nextInt(1_000_000));
        running=true;
        acceptor=new Thread(this::acceptLoop,"edo-accept");acceptor.setDaemon(true);acceptor.start();
        discovery=new UdpDiscovery(listener.getLocalPort(),scheme());discovery.start();
        return listener.getLocalPort();
    }
    public boolean isRunning() {return running;}
    public boolean isSecure(){return tlsEnabled;}
    public String scheme(){return tlsEnabled?"https":"http";}
    public int port(){return listener==null?0:listener.getLocalPort();}
    public String pin(){return pin;}
    public int activeTransfers(){return active.get();}
    public long totalBytes(){return transferred.get();}
    private void acceptLoop() {
        while(running) {
            try {
                Socket socket=listener.accept();
                socket.setSoTimeout(45000);
                socket.setTcpNoDelay(true);
                try {workers.execute(()->serve(socket));}
                catch(RejectedExecutionException e) {socket.close();}
            }catch(IOException e) {if(running) { /* may be transient */ }}
        }
    }
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;
        while((c=in.read())!=-1) {
            if(c=='\n')break;
            if(c!='\r')b.write(c);
            if(b.size()>8192)throw new IOException("Header too long");
        }
        if(c==-1 && b.size()==0)return null;
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }
    private void serve(Socket socket) {
        try(Socket s=socket; InputStream in=new BufferedInputStream(s.getInputStream()); OutputStream out=new BufferedOutputStream(s.getOutputStream())) {
          try {
            InetAddress addr=s.getInetAddress();
            if(!(addr.isSiteLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress())){reply(out,403,"text/plain","LAN only");return;}
            String line=readLine(in);if(line==null)return;
            String[] first=line.split(" ",3);
            if(first.length!=3 || !first[2].startsWith("HTTP/1.")){reply(out,400,"text/plain","Bad request");return;}
            String method=first[0], target=first[1];
            if(target.length()>4096){reply(out,414,"text/plain","URI too long");return;}
            Map<String,String> headers=new HashMap<>();int total=0;
            while((line=readLine(in))!=null && !line.isEmpty()) {
                total+=line.length();if(total>16384)throw new IOException("Header too long");
                int sep=line.indexOf(':');if(sep<=0)throw new IOException("Invalid header");
                headers.putIfAbsent(line.substring(0,sep).trim().toLowerCase(Locale.ROOT),line.substring(sep+1).trim());
            }
            String rawPath=target.split("\\?",2)[0];
            // Browsers visiting unrelated sites cannot invoke Edo's LAN API.
            if("cross-site".equalsIgnoreCase(headers.get("sec-fetch-site")) && rawPath.startsWith("/api/")) {
                reply(out,403,"application/json","{\"error\":\"Cross-site browser request denied\"}");return;
            }
            Map<String,String> query=parseQuery(target.indexOf('?')<0?"":target.substring(target.indexOf('?')+1));
            // Safe, public reachability probe. Never disclose PIN, sessions, files or secrets.
            if(method.equals("GET") && rawPath.equals("/api/health")) {
                reply(out,200,"application/json","{\"app\":\"Edo\",\"version\":\"1.0\",\"ready\":true,\"transport\":"+json(scheme())+",\"pairingRequired\":true}");return;
            }
            if((method.equals("GET") || method.equals("HEAD")) && (rawPath.equals("/")||rawPath.equals("/index.html"))) {
                try(InputStream page=homePage.open()) {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[16384];int n;
                    while((n=page.read(block))!=-1){bytes.write(block,0,n);if(bytes.size()>500_000)throw new IOException("Page too large");}
                    replyBytes(out,200,"text/html; charset=utf-8",bytes.toByteArray(),null);
                }
                return;
            }
            if(method.equals("POST")&&rawPath.equals("/api/pair")) {
                Attempt a=attempts.computeIfAbsent(addr.getHostAddress(),k->new Attempt());
                synchronized(a) {
                    if(System.currentTimeMillis()<a.blockedUntil){reply(out,429,"application/json","{\"error\":\"Too many attempts. Try again in one minute.\"}");return;}
                    if(!pin.equals(query.get("pin"))) {
                        a.failures++;if(a.failures>=5){a.blockedUntil=System.currentTimeMillis()+60000;a.failures=0;}
                        reply(out,401,"application/json","{\"error\":\"Incorrect PIN\"}");return;
                    }
                    a.failures=0;a.blockedUntil=0;
                }
                byte[] token=new byte[24];random.nextBytes(token);
                String sid=Base64.getUrlEncoder().withoutPadding().encodeToString(token);
                String label=query.getOrDefault("device","Browser");
                boolean remember="1".equals(query.get("remember"));
                if(requireApproval) {
                    String rid=UUID.randomUUID().toString();Pending pendingReq=new Pending(rid,label,sid);pending.put(rid,pendingReq);
                    if(remember)pendingRemember.add(rid);
                    reply(out,202,"application/json","{\"pending\":true,\"id\":"+json(rid)+"}");return;
                }
                sessions.put(sid,System.currentTimeMillis()+SESSION_MS);
                if(remember)trust.add(sid,label);
                replyBytes(out,200,"application/json","{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                     cookieHeader(sid,remember,tlsEnabled));return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/pair/status")) {
                String id=query.get("id");Pending p=pending.get(id);
                if(p==null){reply(out,404,"application/json","{\"error\":\"Pairing request missing\"}");return;}
                if(p.expires<System.currentTimeMillis()){pending.remove(id);pendingRemember.remove(id);reply(out,410,"application/json","{\"error\":\"Pairing expired\"}");return;}
                if("denied".equals(p.state)){pending.remove(id);pendingRemember.remove(id);reply(out,403,"application/json","{\"error\":\"TV rejected pairing\"}");return;}
                if("waiting".equals(p.state)){reply(out,202,"application/json","{\"pending\":true}");return;}
                boolean remember=pendingRemember.remove(id);pending.remove(id);
                sessions.put(p.session,System.currentTimeMillis()+SESSION_MS);
                if(remember)trust.add(p.session,p.name);
                replyBytes(out,200,"application/json","{\"ok\":true}".getBytes(StandardCharsets.UTF_8),cookieHeader(p.session,remember,tlsEnabled));return;
            }
            if(rawPath.equals("/dav")||rawPath.startsWith("/dav/")){
                if(!tlsEnabled){reply(out,403,"text/plain","WebDAV requires HTTPS. Enable TLS in Edo settings.");return;}
                if(!WebDav.authorized(headers.get("authorization"),davPassword)){
                    replyBytes(out,401,"text/plain", "WebDAV credentials required".getBytes(StandardCharsets.UTF_8),"WWW-Authenticate: Basic realm=\"Edo WebDAV\"\r\n");return;
                }
                if((method.equals("PUT")||method.equals("MKCOL")||method.equals("DELETE")||method.equals("MOVE")||method.equals("COPY"))&&
                    headers.containsKey("origin")&&!sameOrigin(headers.get("origin"),headers.get("host"))){
                    reply(out,403,"text/plain","Cross-origin writes denied");return;
                }
                WebDav.handle(repository,method,rawPath,headers,in,out);return;
            }
            if(!authorized(headers)){reply(out,401,"application/json","{\"error\":\"Pair with the PIN shown on your TV\"}");return;}
            if(method.equals("GET")&&rawPath.equals("/api/status")) {
                reply(out,200,"application/json","{\"connected\":true,\"active\":"+active.get()+",\"bytes\":"+transferred.get()+",\"free\":"+repository.freeBytes()+",\"approval\":"+requireApproval+",\"remoteAllowed\":"+remoteEnabled+"}");return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/diagnostics")) {
                List<Entry> entries=repository.list();
                List<String> folderList=repository.folders();
                StringBuilder info=new StringBuilder("{\"service\":\"Edo\",\"version\":\"1.0\",\"port\":").append(port())
                    .append(",\"maxChunk\":").append(ResumeStore.MAX_CHUNK)
                    .append(",\"encrypted\":").append(tlsEnabled).append(",\"active\":").append(active.get())
                    .append(",\"bytes\":").append(transferred.get())
                    .append(",\"free\":").append(repository.freeBytes())
                    .append(",\"approval\":").append(requireApproval).append(",\"remoteAllowed\":").append(remoteEnabled)
                    .append(",\"pending\":").append(pendingRequests().size())
                    .append(",\"trusted\":").append(trust.count())
                    .append(",\"files\":").append(entries.size())
                    .append(",\"folders\":").append(folderList.size())
                    .append(",\"clipboard\":").append(clipboard.isEmpty()?0:clipboard.length())
                    .append(",\"features\":[\"resume\",\"range-downloads\",\"zip-export\",\"clipboard\",\"trusted-devices\",\"tv-approval\",\"received-files-browser\",\"tv-share\"]}");
                reply(out,200,"application/json",info.toString());return;
            }
            if(method.equals("POST")&&rawPath.equals("/api/remote/action")){
                String action=query.getOrDefault("action","");
                if(!remoteEnabled||remoteCommand==null){reply(out,403,"application/json","{\"error\":\"Enable remote control on the TV first\"}");return;}
                if(!Arrays.asList("up","down","left","right","select","back","home","files").contains(action)){
                    reply(out,400,"application/json","{\"error\":\"Unsupported remote action\"}");return;
                }
                String origin=headers.get("origin");
                if(origin!=null&&!sameOrigin(origin,headers.get("host"))){reply(out,403,"application/json","{\"error\":\"Cross-origin remote command rejected\"}");return;}
                remoteCommand.accept(action);
                reply(out,200,"application/json","{\"ok\":true}");return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/library")) {
                reply(out,200,"application/json",LibraryIndex.json(repository.list(),repository.freeBytes()));return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/duplicates")) {
                String name=cleanName(query.get("name"));String folder=query.getOrDefault("folder","");long size;
                try{size=Long.parseLong(query.getOrDefault("size","-1"));}catch(Exception e){size=-1;}
                if(name==null||!validFolder(folder)||size<0){reply(out,400,"application/json","{\"error\":\"Invalid lookup\"}");return;}
                reply(out,200,"application/json",LibraryIndex.duplicates(repository.list(),name,folder,size));return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/files")) {
                List<Entry> list=repository.list();StringBuilder j=new StringBuilder("{\"files\":[");
                for(int i=0;i<list.size();i++){
                    Entry e=list.get(i);if(i>0)j.append(',');
                    j.append("{\"id\":").append(json(e.id)).append(",\"name\":").append(json(e.name))
                        .append(",\"size\":").append(e.size).append(",\"mime\":").append(json(e.mime))
                        .append(",\"source\":").append(json(e.source)).append(",\"folder\":").append(json(e.folder)).append('}');
                }
                reply(out,200,"application/json",j.append("]}").toString());return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/sync/index")) {
                StringBuilder response=new StringBuilder();int count=0;
                for(Entry e:repository.list()){
                    if(count++>=10000)break;
                    response.append(base64(e.folder)).append('\t').append(base64(e.name)).append('\t')
                        .append(base64(e.id)).append('\t').append(e.size).append('\n');
                }
                reply(out,200,"text/plain; charset=utf-8",response.toString());return;
            }
            if(method.equals("GET")&&rawPath.equals("/api/history")){reply(out,200,"application/json",journal.json());return;}
            if(method.equals("GET")&&rawPath.equals("/api/devices")){reply(out,200,"application/json",devices());return;}
            if(method.equals("GET")&&rawPath.equals("/api/folders")){StringBuilder b=new StringBuilder("{\"folders\":[");int i=0;for(String f:repository.folders()){if(i++>0)b.append(',');b.append(json(f));}reply(out,200,"application/json",b.append("]}").toString());return;}
            if(method.equals("GET")&&rawPath.equals("/api/clipboard")){reply(out,200,"application/json","{\"text\":"+json(clipboard)+",\"time\":"+clipboardTime+"}");return;}
            if(method.equals("GET")&&rawPath.equals("/api/upload/status")) {
                try{ResumeStore.Job j=resume.find(query.get("id"));reply(out,200,"application/json","{\"id\":"+json(j.id)+",\"offset\":"+j.offset+",\"size\":"+j.size+"}");}
                catch(FileNotFoundException e){reply(out,404,"application/json","{\"error\":\"Upload not found\"}");}
                return;
            }
            if((method.equals("GET")||method.equals("HEAD"))&&(rawPath.equals("/api/file")||rawPath.equals("/api/preview"))) {
                String id=query.get("id");if(id==null) {reply(out,400,"application/json","{\"error\":\"Missing id\"}");return;}
                boolean preview=rawPath.equals("/api/preview");
                try(Opened file=repository.open(id)){
                    if(file.length<0){reply(out,500,"text/plain","Unknown file length");return;}
                    String mime=file.mime==null?"application/octet-stream":file.mime;
                    if(preview&&!isPreviewType(mime)){reply(out,403,"application/json","{\"error\":\"Preview unavailable for this file type\"}");return;}
                    long from=0,to=file.length==0?-1:file.length-1;int response=200;
                    String range=headers.get("range");
                    if(range!=null&&range.startsWith("bytes=")){
                        String[] parts=range.substring(6).split("-",2);
                        try{if(parts[0].isEmpty()){long tail=Long.parseLong(parts[1]);if(tail<=0)throw new NumberFormatException();from=Math.max(0,file.length-tail);}else from=Long.parseLong(parts[0]);if(parts.length>1&&!parts[1].isEmpty()&&!parts[0].isEmpty())to=Math.min(to,Long.parseLong(parts[1]));if(from<0||from>=file.length||to<from)throw new NumberFormatException();response=206;}
                        catch(Exception e){header(out,416,"application/json",0,"Content-Range: bytes */"+file.length+"\r\n");return;}
                    }
                    long length=to<from?0:to-from+1;
                    String extras="Accept-Ranges: bytes\r\n"+(response==206?"Content-Range: bytes "+from+"-"+to+"/"+file.length+"\r\n":"")+
                        "Content-Disposition: "+(preview?"inline":"attachment")+"; filename=\"download\"; filename*=UTF-8''"+encode(file.name)+"\r\n";
                    header(out,response,mime,length,extras);
                    if(method.equals("GET")){skipFully(file.stream,from);active.incrementAndGet();try{copy(file.stream,out,length,true);transferred.addAndGet(length);journal.record("download",file.name,length,true);}finally{active.decrementAndGet();}}
                    out.flush();
                }
                return;
            }
            if(method.equals("POST")||method.equals("PUT")||(method.equals("GET")&&rawPath.equals("/api/zip"))){
                String origin=headers.get("origin");
                if(origin!=null&&!sameOrigin(origin,headers.get("host"))){reply(out,403,"application/json","{\"error\":\"Cross-origin request denied\"}");return;}
                if(rawPath.equals("/api/upload/init")&&method.equals("POST")){
                    String name=cleanName(query.get("name")),folder=query.getOrDefault("folder","");long length;
                    try{length=Long.parseLong(query.getOrDefault("size","-1"));}catch(Exception e){length=-1;}
                    if(name==null||!validFolder(folder)||length<0||length>MAX_FILE){reply(out,400,"application/json","{\"error\":\"Invalid file or folder\"}");return;}
                    long free=repository.freeBytes(),staging=resume.tempFreeBytes();
                    if((free>=0&&length>free-32L*1024*1024)||(staging>=0&&length>staging-32L*1024*1024)){
                        reply(out,507,"application/json","{\"error\":\"Insufficient space for transfer staging or destination\"}");return;
                    }
                    ResumeStore.Job j=resume.begin(name,folder,length,query.get("resume"));
                    reply(out,200,"application/json","{\"id\":"+json(j.id)+",\"offset\":"+j.offset+",\"size\":"+j.size+",\"chunk\":"+ResumeStore.MAX_CHUNK+"}");return;
                }
                if(rawPath.equals("/api/upload/chunk")&&method.equals("PUT")){
                    long length=parseLength(headers);long offset;
                    try{offset=Long.parseLong(query.getOrDefault("offset","-1"));}catch(Exception e){offset=-1;}
                    if(length<0||length>ResumeStore.MAX_CHUNK||offset<0){reply(out,400,"application/json","{\"error\":\"Invalid chunk\"}");return;}
                    ResumeStore.Job job=resume.find(query.get("id"));
                    if(job.offset!=offset || offset+length>job.size) {
                        discard(in,length);
                        reply(out,409,"application/json","{\"error\":\"Chunk offset mismatch. Request current status and retry.\",\"offset\":"+job.offset+"}");return;
                    }
                    active.incrementAndGet();try{resume.append(query.get("id"),offset,in,length);transferred.addAndGet(length);}finally{active.decrementAndGet();}
                    ResumeStore.Job j=resume.find(query.get("id"));reply(out,200,"application/json","{\"offset\":"+j.offset+"}");return;
                }
                if(rawPath.equals("/api/upload/finish")&&method.equals("POST")){
                    ResumeStore.Job j=resume.find(query.get("id"));LanServer.Entry e=resume.finish(j.id,repository,query.get("sha256"));journal.record("upload",e.name,e.size,true);
                    reply(out,201,"application/json","{\"ok\":true,\"id\":"+json(e.id)+",\"name\":"+json(e.name)+"}");return;
                }
                if(rawPath.equals("/api/upload/cancel")&&method.equals("POST")){resume.cancel(query.get("id"));reply(out,200,"application/json","{\"ok\":true}");return;}
                if(rawPath.equals("/api/clipboard")&&method.equals("POST")){
                    long n=parseLength(headers);if(n<0||n>32768){reply(out,413,"application/json","{\"error\":\"Text exceeds 32 KB\"}");return;}
                    ByteArrayOutputStream clip=new ByteArrayOutputStream();copy(in,clip,n,false);byte[] data=clip.toByteArray();clipboard=new String(data,StandardCharsets.UTF_8);clipboardTime=System.currentTimeMillis();
                    journal.record("clipboard","Text shared",data.length,true);reply(out,200,"application/json","{\"ok\":true}");return;
                }
                if(method.equals("POST")&&(rawPath.equals("/api/mkdir")||rawPath.equals("/api/rename")||rawPath.equals("/api/move")||rawPath.equals("/api/copy")||rawPath.equals("/api/rmdir"))){
                    String folder=query.getOrDefault("folder","");if(!validFolder(folder)){reply(out,400,"application/json","{\"error\":\"Invalid folder path\"}");return;}
                    if(rawPath.equals("/api/mkdir"))repository.mkdir(folder);
                    if(rawPath.equals("/api/rmdir"))repository.removeFolder(folder);
                    if(rawPath.equals("/api/rename")){String name=cleanName(query.get("name"));if(name==null)throw new IllegalArgumentException("Invalid name");repository.rename(query.get("id"),name);}
                    if(rawPath.equals("/api/move"))repository.move(query.get("id"),folder);
                    if(rawPath.equals("/api/copy"))repository.copy(query.get("id"),folder);
                    reply(out,200,"application/json","{\"ok\":true}");return;
                }
                if(rawPath.equals("/api/device/revoke")&&method.equals("POST")){revokeDevice(query.get("id"));reply(out,200,"application/json","{\"ok\":true}");return;}
                if(rawPath.equals("/api/zip")&&method.equals("GET")){
                    String raw=query.getOrDefault("ids","");String[] ids=raw.split(",");
                    if(ids.length==0||ids.length>100){reply(out,400,"application/json","{\"error\":\"Choose 1–100 files\"}");return;}
                    // Ensure the temporary ZIP does not consume all free space.
                    long totalSize=0;for(String id:ids){try(Opened f=repository.open(id)){if(f.length>20L*1024*1024*1024||totalSize>20L*1024*1024*1024-f.length){reply(out,413,"application/json","{\"error\":\"ZIP is limited to 20 GiB; download larger files individually\"}");return;}totalSize+=f.length;}}
                    long free=repository.freeBytes();if(free>=0&&totalSize+64*1024*1024>free){reply(out,507,"application/json","{\"error\":\"Insufficient space for temporary ZIP\"}");return;}
                    // ZIP is assembled on disk, never buffered in RAM, before the Content-Length is sent.
                    File zip=File.createTempFile("dropwave-", ".zip");
                    try{
                        Set<String> names=new HashSet<>();
                        try(ZipOutputStream zout=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                            for(String id:ids){try(Opened file=repository.open(id)){
                                String name=cleanName(file.name);if(name==null)continue;
                                String candidate=name;int suffix=2;while(!names.add(candidate))candidate=(suffix++)+"-"+name;
                                zout.putNextEntry(new ZipEntry(candidate));copy(file.stream,zout,file.length,false);zout.closeEntry();
                            }}
                        }
                        header(out,200,"application/zip",zip.length(),"Content-Disposition: attachment; filename=\"Edo-files.zip\"\r\n");
                        try(InputStream src=new FileInputStream(zip)){copy(src,out,zip.length(),false);}
                    }finally{zip.delete();}return;
                }
            }
            if(method.equals("POST")&&(rawPath.equals("/api/upload")||rawPath.equals("/api/delete"))) {
                // Same-origin write requests only (cross-site forms cannot modify the TV).
                String origin=headers.get("origin");
                if(origin!=null&&!sameOrigin(origin,headers.get("host"))){reply(out,403,"application/json","{\"error\":\"Cross-origin request denied\"}");return;}
                if(rawPath.equals("/api/delete")) {
                    String id=query.get("id");if(id==null){reply(out,400,"application/json","{\"error\":\"Missing id\"}");return;}
                    repository.remove(id);journal.record("delete",id,0,true);reply(out,200,"application/json","{\"ok\":true}");return;
                }
                String name=cleanName(query.get("name"));
                if(name==null){reply(out,400,"application/json","{\"error\":\"Invalid filename\"}");return;}
                if(headers.containsKey("transfer-encoding")) {reply(out,411,"application/json","{\"error\":\"Content-Length required\"}");return;}
                long length;
                try{length=Long.parseLong(headers.getOrDefault("content-length","-1"));}catch(NumberFormatException e){length=-1;}
                if(length<0||length>MAX_FILE){reply(out,413,"application/json","{\"error\":\"Invalid file size\"}");return;}
                active.incrementAndGet();
                try {
                    Entry e=repository.upload(name,new LimitedInputStream(in,length,transferred),length);
                    journal.record("upload",e.name,length,true);
                    reply(out,201,"application/json","{\"ok\":true,\"name\":"+json(e.name)+"}");
                } finally {active.decrementAndGet();}
                return;
            }
            reply(out,404,"application/json","{\"error\":\"Not found\"}");
          } catch(Exception e) {
            try{int code=e instanceof FileNotFoundException?404:e instanceof IllegalArgumentException?400:e instanceof FileAlreadyExistsException?409:500;
                reply(out,code,"application/json","{\"error\":\""+jsonString(e.getMessage()==null?"Operation failed":e.getMessage())+"\"}");}catch(Exception ignored){}
          }
        }catch(Exception ignored){}
    }
    private boolean authorized(Map<String,String> headers) {
        String cookies=headers.getOrDefault("cookie","");
        for(String cookie:cookies.split(";")){
            String c=cookie.trim();if(c.startsWith("dw_session=")){
                Long expiry=sessions.get(c.substring(11));return (expiry!=null&&expiry>System.currentTimeMillis())||trust.allows(c.substring(11));
            }
        }return false;
    }
    private static String cookieHeader(String sid,boolean remember,boolean secure){return "Set-Cookie: dw_session="+sid+"; Path=/; Max-Age="+(remember?2592000:43200)+"; HttpOnly; SameSite=Strict"+(secure?"; Secure":"")+"\r\n";}
    private static long parseLength(Map<String,String> headers){if(headers.containsKey("transfer-encoding"))return -1;try{return Long.parseLong(headers.getOrDefault("content-length","-1"));}catch(Exception e){return -1;}}
    private static boolean isPreviewType(String mime){return mime!=null&&(mime.startsWith("image/jpeg")||mime.startsWith("image/png")||mime.startsWith("image/gif")||mime.startsWith("image/webp")||mime.startsWith("video/mp4")||mime.startsWith("video/webm")||mime.startsWith("audio/mpeg")||mime.startsWith("audio/mp4")||mime.startsWith("audio/ogg")||mime.startsWith("application/pdf"));}
    private static void discard(InputStream in,long n)throws IOException{byte[] buffer=new byte[8192];while(n>0){int k=in.read(buffer,0,(int)Math.min(n,buffer.length));if(k<0)break;n-=k;}}
    private static void skipFully(InputStream in,long n)throws IOException{while(n>0){long k=in.skip(n);if(k<=0){if(in.read()<0)throw new EOFException("Cannot seek");k=1;}n-=k;}}
    private boolean sameOrigin(String origin,String host) {
        // Enforce schemeful same-origin checks: HTTPS must not accept writes from HTTP origins.
        if(host==null||origin==null)return false;
        return origin.equalsIgnoreCase(scheme()+"://"+host);
    }
    private static Map<String,String> parseQuery(String raw) {
        Map<String,String> result=new HashMap<>();
        for(String part:raw.split("&")){
            if(part.isEmpty())continue;String[] p=part.split("=",2);
            try{result.put(URLDecoder.decode(p[0],"UTF-8"),URLDecoder.decode(p.length==2?p[1]:"","UTF-8"));}
            catch(Exception ignored){}
        }
        return result;
    }
    public static String cleanName(String filename) {
        if(filename==null || filename.isEmpty() || filename.length()>240)return null;
        String n=filename.replaceAll("[\\\\/\\x00-\\x1F\\x7F]","_").trim();
        if(n.equals(".")||n.equals("..")||n.isEmpty())return null;
        return n;
    }
    private static String encode(String name) throws UnsupportedEncodingException {return URLEncoder.encode(name,"UTF-8").replace("+","%20");}
    private static String jsonString(String text) {
        if(text==null)return "";StringBuilder b=new StringBuilder();
        for(char c:text.toCharArray())switch(c){case '"': b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(' ');else b.append(c);}
        return b.toString();
    }
    public static boolean validFolder(String p){
        if(p==null)return false;if(p.isEmpty())return true;if(p.length()>400||p.startsWith("/")||p.endsWith("/"))return false;
        for(String part:p.split("/",-1))if(cleanName(part)==null||!cleanName(part).equals(part)||part.equals(".")||part.equals(".."))return false;
        return true;
    }
    public static String json(String t){return '"'+jsonString(t)+'"';}
    private static void reply(OutputStream o,int status,String type,String text) throws IOException {replyBytes(o,status,type,text.getBytes(StandardCharsets.UTF_8),null);}
    private static void replyBytes(OutputStream o,int status,String type,byte[] bytes,String extra) throws IOException {
        header(o,status,type,bytes.length,extra);o.write(bytes);o.flush();
    }
    private static String base64(String text){return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
    private static String reason(int status){switch(status){case 200:return "OK";case 202:return "Accepted";case 206:return "Partial Content";case 409:return "Conflict";case 410:return "Gone";case 416:return "Range Not Satisfiable";case 507:return "Insufficient Storage";case 201:return "Created";case 400:return "Bad Request";case 401:return "Unauthorized";case 403:return "Forbidden";case 404:return "Not Found";case 411:return "Length Required";case 413:return "Content Too Large";case 414:return "URI Too Long";case 429:return "Too Many Requests";default:return "Internal Server Error";}}
    private static void header(OutputStream o,int status,String type,long length,String extra) throws IOException {
        String h="HTTP/1.1 "+status+" "+reason(status)+"\r\n"+
            "Content-Type: "+type+"\r\nContent-Length: "+length+"\r\nConnection: close\r\n"+
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"+
            "Referrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n"+
            "Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'self'\r\n"+
            (extra==null?"":extra)+"\r\n";
        o.write(h.getBytes(StandardCharsets.UTF_8));o.flush();
    }
    private static void copy(InputStream src,OutputStream dst,long length,boolean ignoreExtra) throws IOException {
        byte[] buffer=new byte[128*1024];long remaining=length;
        while(remaining>0){int n=src.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n==-1)throw new EOFException("Transfer interrupted");dst.write(buffer,0,n);remaining-=n;}
        dst.flush();
    }
    public static void copyExactly(InputStream src,OutputStream dst,long length) throws IOException {copy(src,dst,length,false);}
    private static class LimitedInputStream extends FilterInputStream {
        long remaining;AtomicLong counter;
        LimitedInputStream(InputStream i,long n,AtomicLong c){super(i);remaining=n;counter=c;}
        @Override public int read() throws IOException {if(remaining<=0)return -1;int b=super.read();if(b>=0){remaining--;counter.incrementAndGet();}return b;}
        @Override public int read(byte[] b,int off,int len) throws IOException {if(remaining<=0)return -1;int n=in.read(b,off,(int)Math.min(remaining,len));if(n>0){remaining-=n;counter.addAndGet(n);}return n;}
    }
    @Override public synchronized void close() {
        running=false;
        if(listener!=null)try{listener.close();}catch(IOException ignored){}
        if(discovery!=null)discovery.close();
        workers.shutdownNow();sessions.clear();pending.clear();
    }
}
