package com.sxw.sxwaiagent.memory;

import com.sxw.sxwaiagent.knowledge.EmbeddingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class MemoryEmbeddingService {
 private final JdbcTemplate jdbc; private final EmbeddingService embeddings;
 public MemoryEmbeddingService(JdbcTemplate jdbc,EmbeddingService embeddings){this.jdbc=jdbc;this.embeddings=embeddings;}
 public void index(String memoryId,String text){
  Integer active=jdbc.queryForObject("SELECT count(*) FROM ai_memory_item WHERE memory_id=? AND status='ACTIVE' AND deleted_at IS NULL AND purge_requested_at IS NULL",Integer.class,memoryId);
  if(active==null||active==0)return;
  float[] vector=embeddings.embed("document: "+text);if(vector.length!=1024)return;String hash=hash(text);String literal=vector(vector);
  jdbc.update("INSERT INTO ai_memory_embedding(memory_id,model,dimension,content_hash,embedding,status) VALUES (?,'text-embedding-v3',1024,?,?::vector,'READY') ON CONFLICT (memory_id,model,dimension,content_hash) DO NOTHING",memoryId,hash,literal);
  int activated=jdbc.update("UPDATE ai_memory_item SET active_embedding_id=(SELECT id FROM ai_memory_embedding WHERE memory_id=? AND status='READY' AND content_hash=? ORDER BY id DESC LIMIT 1) WHERE memory_id=? AND status='ACTIVE' AND deleted_at IS NULL AND purge_requested_at IS NULL",memoryId,hash,memoryId);
  if(activated==0)jdbc.update("DELETE FROM ai_memory_embedding WHERE memory_id=? AND content_hash=?",memoryId,hash);
 }
 public void purge(String memoryId,long userId){
  int owned=jdbc.queryForObject("SELECT count(*) FROM ai_memory_item WHERE memory_id=? AND user_id=? AND status='DELETED'",Integer.class,memoryId,userId);
  if(owned==0)throw new IllegalArgumentException("Memory purge target not found");
  jdbc.update("DELETE FROM ai_memory_embedding WHERE memory_id=?",memoryId);
  jdbc.update("UPDATE ai_memory_candidate SET title='[PURGED]',content='[PURGED]',content_hash=NULL,source_message_id=NULL,source_conversation_id=NULL WHERE applied_memory_id=? AND user_id=?",memoryId,userId);
  jdbc.update("UPDATE ai_memory_item SET name='[PURGED]',description=NULL,rule_text=NULL,why_text=NULL,apply_text=NULL,content_hash=NULL,source_candidate_id=NULL,source_conversation_id=NULL,active_embedding_id=NULL,purged_at=NOW() WHERE memory_id=? AND user_id=? AND status='DELETED'",memoryId,userId);
 }
 private static String vector(float[] v){StringBuilder b=new StringBuilder("[");for(int i=0;i<v.length;i++){if(i>0)b.append(',');b.append(v[i]);}return b.append(']').toString();}
 private static String hash(String t){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){return "";}}
}
