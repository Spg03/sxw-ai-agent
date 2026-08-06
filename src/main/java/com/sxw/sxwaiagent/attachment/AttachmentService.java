package com.sxw.sxwaiagent.attachment;
import io.minio.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*; import java.security.*; import java.util.*;

@Service public class AttachmentService {
 private static final Set<String> EXT=Set.of("txt","md","pdf","docx"); private final JdbcTemplate jdbc; private final MinioClient minio; private final AttachmentProperties p;
 public AttachmentService(JdbcTemplate jdbc,MinioClient minio,AttachmentProperties p){this.jdbc=jdbc;this.minio=minio;this.p=p;}
 public Map<String,Object> upload(long userId,String conversationId,MultipartFile file) throws Exception {
  if(!p.enabled()) throw new IllegalStateException("附件服务未启用"); if(file.isEmpty()||file.getSize()>p.maxBytes()) throw new IllegalArgumentException("附件为空或超过10MB");
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("attachment"); String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase():""; if(!EXT.contains(ext)) throw new IllegalArgumentException("仅支持 TXT、MD、PDF、DOCX");
  byte[] bytes=file.getBytes(); String id="att_"+UUID.randomUUID().toString().replace("-","").substring(0,24); String key="chat/"+userId+"/"+conversationId+"/"+id+"-"+name.replaceAll("[^\\w. -]","_");
  if(!minio.bucketExists(BucketExistsArgs.builder().bucket(p.bucket()).build())) minio.makeBucket(MakeBucketArgs.builder().bucket(p.bucket()).build());
  minio.putObject(PutObjectArgs.builder().bucket(p.bucket()).object(key).stream(new ByteArrayInputStream(bytes),bytes.length,-1).contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream")).build());
  String text=extract(ext,bytes); if(text.length()>30000) text=text.substring(0,30000);
  jdbc.update("INSERT INTO ai_chat_attachment(attachment_id,user_id,conversation_id,original_name,content_type,size_bytes,sha256,object_key,extracted_text,status) VALUES (?,?,?,?,?,?,?,?,?,?)",id,userId,conversationId,name,file.getContentType(),bytes.length,sha256(bytes),key,text,"READY");
  return Map.of("attachmentId",id,"name",name,"sizeBytes",bytes.length,"status","READY"); }
 public List<Map<String,Object>> list(long user,String conversation){return jdbc.query("SELECT attachment_id,original_name,content_type,size_bytes,status,created_at FROM ai_chat_attachment WHERE user_id=? AND conversation_id=? ORDER BY created_at DESC",(rs,n)->Map.<String,Object>of("attachmentId",rs.getString(1),"name",rs.getString(2),"contentType",Optional.ofNullable(rs.getString(3)).orElse("application/octet-stream"),"sizeBytes",rs.getLong(4),"status",rs.getString(5),"createdAt",rs.getTimestamp(6).toInstant().toString()),user,conversation);}
 public String contextText(long user,String conversation,List<String> ids){ if(ids==null||ids.isEmpty()) return ""; String placeholders=String.join(",",Collections.nCopies(ids.size(),"?")); List<Object> args=new ArrayList<>();args.add(user);args.add(conversation);args.addAll(ids); List<String> rows=jdbc.query("SELECT original_name,extracted_text FROM ai_chat_attachment WHERE user_id=? AND conversation_id=? AND status='READY' AND attachment_id IN ("+placeholders+")",(rs,n)->"FILE: "+rs.getString(1)+"\n"+Optional.ofNullable(rs.getString(2)).orElse(""),args.toArray()); String result=String.join("\n\n",rows);return result.length()>24000?result.substring(0,24000):result;}
 public void delete(long user,String conversation,String id) throws Exception {List<String> keys=jdbc.query("SELECT object_key FROM ai_chat_attachment WHERE attachment_id=? AND user_id=? AND conversation_id=?",(rs,n)->rs.getString(1),id,user,conversation);if(keys.isEmpty())throw new IllegalArgumentException("Attachment not found");jdbc.update("DELETE FROM ai_chat_attachment WHERE attachment_id=? AND user_id=? AND conversation_id=?",id,user,conversation);try{minio.removeObject(RemoveObjectArgs.builder().bucket(p.bucket()).object(keys.getFirst()).build());}catch(Exception ignored){}}
 private static String extract(String ext, byte[] bytes) throws IOException { if("txt".equals(ext)||"md".equals(ext)) return new String(bytes,java.nio.charset.StandardCharsets.UTF_8); if("pdf".equals(ext)){try(PDDocument d=Loader.loadPDF(bytes)){return new PDFTextStripper().getText(d);}} try(XWPFDocument d=new XWPFDocument(new ByteArrayInputStream(bytes))){return d.getParagraphs().stream().map(x->x.getText()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("\n"));} }
 private static String sha256(byte[] b)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format("%02x",x));return s.toString();}
}
