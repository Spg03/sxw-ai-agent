package com.sxw.sxwaiagent.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserMemoryService {
    private final JdbcTemplate jdbc;
    public UserMemoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> list(long userId, String status) {
        return jdbc.query("SELECT memory_id,name,description,memory_type,status,source_kind,always_on,created_at FROM ai_memory_item WHERE user_id=? AND (? IS NULL OR status=?) ORDER BY always_on DESC,created_at DESC LIMIT 100",
                (rs,n) -> Map.of("memoryId",rs.getString(1),"name",rs.getString(2),"description",rs.getString(3),"memoryType",rs.getString(4),"status",rs.getString(5),"sourceKind",rs.getString(6),"alwaysOn",rs.getBoolean(7),"createdAt",rs.getTimestamp(8).toInstant().toString()), userId,status,status);
    }
    public String activeIndex(long userId) { return jdbc.query("SELECT name,description FROM ai_memory_item WHERE user_id=? AND status='ACTIVE' AND always_on=true AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP) ORDER BY activated_at DESC NULLS LAST,created_at DESC LIMIT 5",(rs,n)->"- "+rs.getString(1)+": "+rs.getString(2),userId).stream().reduce("",(a,b)->a.isEmpty()?b:a+'\n'+b); }
    public String relevant(long userId,String query) { String like="%"+query.replace("%", "")+"%"; return jdbc.query("SELECT name,description FROM ai_memory_item WHERE user_id=? AND status='ACTIVE' AND always_on=false AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP) AND (name ILIKE ? OR description ILIKE ?) ORDER BY created_at DESC LIMIT 5",(rs,n)->"- "+rs.getString(1)+": "+rs.getString(2),userId,like,like).stream().reduce("",(a,b)->a.isEmpty()?b:a+'\n'+b); }
    public Map<String,Object> explicit(long userId,String content) { if(content==null||content.isBlank()||content.length()>500)throw new IllegalArgumentException("Invalid memory content"); if(content.matches("(?is).*?(password|secret|api[_ -]?key|token|密码|密钥).*"))throw new IllegalArgumentException("Sensitive information cannot be stored as memory"); String id="mem_"+UUID.randomUUID().toString().replace("-","").substring(0,20); jdbc.update("UPDATE ai_memory_item SET always_on=false WHERE user_id=? AND always_on=true AND status='ACTIVE' AND memory_id IN (SELECT memory_id FROM ai_memory_item WHERE user_id=? AND always_on=true AND status='ACTIVE' ORDER BY activated_at ASC NULLS FIRST,created_at ASC LIMIT 1) AND (SELECT count(*) FROM ai_memory_item WHERE user_id=? AND always_on=true AND status='ACTIVE')>=5",userId,userId,userId); jdbc.update("INSERT INTO ai_memory_item(memory_id,memory_type,name,description,rule_text,why_text,apply_text,confidence,status,created_at,user_id,source_kind,always_on,activated_at) VALUES (?,?,?,?,?,?,?,?, 'ACTIVE',NOW(),?,'EXPLICIT',true,NOW())",id,"USER","User preference",content,content,"Explicitly requested by user",content,new BigDecimal("1.0"),userId); return Map.of("memoryId",id,"status","ACTIVE","alwaysOn",true); }
    public void decide(long userId,String memoryId,boolean approve,boolean alwaysOn) { int updated=jdbc.update("UPDATE ai_memory_item SET status=?,always_on=?,activated_at=CASE WHEN ? THEN NOW() ELSE activated_at END,reviewed_at=NOW(),reviewed_by=? WHERE memory_id=? AND user_id=?",approve?"ACTIVE":"ARCHIVED",approve&&alwaysOn,approve&&alwaysOn,"user:"+userId,memoryId,userId);if(updated==0)throw new IllegalArgumentException("Memory not found"); }
}
