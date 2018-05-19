package com.sinochem.member.biz.impl;
yty5601117

0519提交

0519第二次提交

0519第san次提交

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.eyeieye.melody.util.uuid.UUIDGenerator;
import com.eyeieye.melody.web.url.URLBroker;
import com.github.pagehelper.PageInfo;
import com.sinochem.common.common.MD5;
import com.sinochem.common.enums.EnumSysMessage;
import com.sinochem.common.exception.BizException;
import com.sinochem.common.page.PageUtils;
import com.sinochem.common.service.SysMessageService;
import com.sinochem.common.service.impl.BaseServiceImpl;
import com.sinochem.member.biz.MbMemberService;
import com.sinochem.member.formsBean.MemberShipStatisticsBean;
import com.sinochem.member.mapper.MbMemberMapper;
import com.sinochem.member.model.MbMember;
import com.sinochem.member.model.MbMemberExample;
import com.sinochem.member.model.forms.UserLoginFormsInfo;
import com.sinochem.member.query.MbMemberQuery;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.*;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.nio.charset.CodingErrorAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * 会员管理服务接口实现类
 * Created by bbt on 2016/11/1.
 */
@Service
public class MbMemberServiceImpl
        extends BaseServiceImpl<MbMember, String, MbMemberExample, MbMemberMapper>
        implements MbMemberService {

    @Autowired
    private SysMessageService sysMessageService;

    @Autowired
    private UUIDGenerator uuidGenerator;

    @Autowired
    private URLBroker appServerBroker;

    @Value("${mic.applicationId}")
    private String micApplicationId;
    @Value("${mic.api.member.add}")
    private String micMemberAddEndPoint;
    @Value("${mic.api.member.change}")
    private String micMemberChangeEndPoint;
    /**
     * 最大登录失败次数
     */
    private static final int maxLoginFailedCount = 7;
    /**
     * 登录失败次数过多时，锁定时间（小时）
     */
    private static final int accountLockMinute = 30;
    /**
     * 重置密码密钥有效时间（分钟）
     */
    private static final int Key_Lost_Time = 15;

    private String randomUUID(){
        //return UUID.randomUUID().toString().replace("-", "");
        return uuidGenerator.gain();
    }
    /**
     * 注册会员
     * @param record 会员信息
     * @return 注册成功会员信息，null：注册失败
     * @throws BizException
     */
    //@Transactional("member-transactionManager" )
    @Override
    public MbMember register(MbMember record) throws BizException {
        validRegisterMember(record);
        //设置默认值
        record.setMemberId(randomUUID());
        record.setIsActive("1");//自主注册，默认激活
        record.setPassword(MD5.getMD5(record.getPassword()));//密码加密
        record.setDelFlg("0");
        record.setLoginFailureCount(0);
        record.setIsAccountLocked("0");
        record.setIsForbidden("0");//默认非禁用
        Date cur = new Date();
        record.setLoginDate(cur);//注册成功自动登录
        record.setCreateTime(cur);
        record.setCreateUser(record.getMemberId());
        record.setUpdateTime(cur);
        record.setUpdateUser(record.getMemberId());
        //保存
        int result = getMapper().insert(record);
        if (result < 1){
            throw new BizException("会员信息保存失败！");
        }
        //写入到MIC
        log.debug("会员注册成功,并向MIC发送新增会员信息请求...");
        CloseableHttpClient httpClient=null;
        try {
            httpClient = createHttpClient();
            JSONObject recordJsonObject =(JSONObject) JSON.toJSON(record);
            recordJsonObject.put("uid", record.getMemberId());
            recordJsonObject.put("createSource", micApplicationId);
            recordJsonObject.put("updateSource", micApplicationId);
            String requestBody = recordJsonObject.toJSONString();

            log.debug("MIC请求:{}发送\n{}\n:" , micMemberAddEndPoint,requestBody);
            HttpPost postRequest = new HttpPost(micMemberAddEndPoint);
            postRequest.setConfig( RequestConfig.DEFAULT);
            StringEntity stringEntity = new StringEntity(requestBody, Consts.UTF_8);
            stringEntity.setContentType("application/json");
            postRequest.setEntity(stringEntity);
            CloseableHttpResponse httpResponse = httpClient.execute(postRequest);
            // 获取响应消息实体
            HttpEntity entityRep = httpResponse.getEntity();
            String responseContent = EntityUtils.toString(entityRep, Consts.UTF_8);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                JSONObject jsonObject = JSON.parseObject(responseContent);
                String msg = jsonObject.getString("msg");
                if (jsonObject.getBoolean("success")) {
                    log.info("会员注册成功,会员中心返回："+msg);
                }else{
                    log.error("会员注册失败,会员中心返回："+msg);
                    throw new BizException("会员中心调用失败,返回:"+msg);
                }
            }else{
                log.error("会员中心调用失败,服务器未正常响应:"+responseContent);
                throw new BizException("会员中心调用失败,服务器未正常响应:"+responseContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("向MIC发送新增会员信息请求异常："+e.getMessage());
           // throw new BizException("会员中心处理失败,返回"+e.getMessage());
        }
        //写入到MIC完成
        //发送注册成功系统消息
        sysMessageService.sendMessage(record.getMemberId(), EnumSysMessage.Enum_Message_Register_Success);
        return record;
    }

    /**
     * 管理员代理注册
     * @param mobile
     *      注册手机号码
     * @return
     * @throws BizException
     */
    //@Transactional("member-transactionManager" )
    @Override
    public MbMember agentRegister(String mobile) throws BizException {
        MbMember record = new MbMember();
        record.setMobile(mobile);
        record.setName(mobile);
        record.setUserName(mobile);
        validRegisterMember(record);
        //设置默认值
        record.setMemberId(randomUUID());
        record.setIsActive("0");//代注册，默认未激活
        record.setPassword(MD5.getMD5("123456"));//密码默认123456，激活会被改掉
        record.setDelFlg("0");
        record.setLoginFailureCount(0);
        record.setIsAccountLocked("0");
        record.setIsForbidden("0");//默认非禁用
        Date cur = new Date();
        record.setLoginDate(cur);
        record.setCreateTime(cur);
        record.setCreateUser(record.getMemberId());
        record.setUpdateTime(cur);
        record.setUpdateUser(record.getMemberId());
        //保存
        int result = getMapper().insert(record);
        if (result < 1){
            throw new BizException("会员信息保存失败！");
        }
        //写入到MIC
        log.debug("会员注册成功,并向MIC发送新增会员信息请求...");
        CloseableHttpClient httpClient=null;
        try {
            httpClient = createHttpClient();
            JSONObject recordJsonObject =(JSONObject) JSON.toJSON(record);
            recordJsonObject.put("uid", record.getMemberId());
            recordJsonObject.put("createSource", micApplicationId);
            recordJsonObject.put("updateSource", micApplicationId);
            String requestBody = recordJsonObject.toJSONString();

            log.debug("MIC请求:{}发送\n{}\n:" , micMemberAddEndPoint,requestBody);
            HttpPost postRequest = new HttpPost(micMemberAddEndPoint);
            postRequest.setConfig( RequestConfig.DEFAULT);
            StringEntity stringEntity = new StringEntity(requestBody, Consts.UTF_8);
            stringEntity.setContentType("application/json");
            postRequest.setEntity(stringEntity);
            CloseableHttpResponse httpResponse = httpClient.execute(postRequest);
            // 获取响应消息实体
            HttpEntity entityRep = httpResponse.getEntity();
            String responseContent = EntityUtils.toString(entityRep, Consts.UTF_8);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                JSONObject jsonObject = JSON.parseObject(responseContent);
                String msg = jsonObject.getString("msg");
                if (jsonObject.getBoolean("success")) {
                    log.debug("代理注册会员注册成功,会员中心返回："+msg);
                }else{
                    log.error("代理注册会员注册失败,会员中心返回："+msg);
                    throw new BizException("代理注册会员信息保存失败,,会员中心返回"+msg);
                }
            }else{
                log.error("会员中心调用失败,服务器未正常响应:"+responseContent);
                throw new BizException("会员中心调用失败,服务器未正常响应:"+responseContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("代理注册向MIC发送新增会员信息请求异常："+e.getMessage());
           // throw new BizException("会员中心处理失败,返回"+e.getMessage());
        }
        //写入到MIC完成
        //发送注册成功系统消息
        sysMessageService.sendMessage(record.getMemberId(), EnumSysMessage.Enum_Message_Register_Success);
        return record;
    }

    /**
     * 管理员代理快速注册
     * @param mobile
     *      注册手机号码和姓名
     * @return
     * @throws BizException
     */
    //@Transactional("member-transactionManager")
    @Override
    public MbMember agentFastRegister(String mobile,String name) throws BizException {
        MbMember record = new MbMember();
        record.setMobile(mobile);
        validRegisterMember(record);
        record.setName(name);
        //设置默认值
        record.setMemberId(randomUUID());
        record.setIsActive("0");//代注册，默认未激活
        record.setPassword(MD5.getMD5("123456"));//密码默认123456，激活会被改掉
        record.setDelFlg("0");
        record.setLoginFailureCount(0);
        record.setIsAccountLocked("0");
        record.setIsForbidden("0");//默认非禁用
        Date cur = new Date();
        record.setLoginDate(cur);
        record.setCreateTime(cur);
        record.setCreateUser(record.getMemberId());
        record.setUpdateTime(cur);
        record.setUpdateUser(record.getMemberId());
        //保存
        int result = getMapper().insert(record);
        if (result < 1){
            throw new BizException("会员信息保存失败！");
        }
        //写入到MIC
        log.debug("会员注册成功,并向MIC发送新增会员信息请求...");
        CloseableHttpClient httpClient=null;
        try {
            httpClient = createHttpClient();
            JSONObject recordJsonObject =(JSONObject) JSON.toJSON(record);
            recordJsonObject.put("uid", record.getMemberId());
            recordJsonObject.put("createSource", micApplicationId);
            recordJsonObject.put("updateSource", micApplicationId);
            String requestBody = recordJsonObject.toJSONString();

            log.debug("MIC请求:{}发送\n{}\n:" , micMemberAddEndPoint,requestBody);
            HttpPost postRequest = new HttpPost(micMemberAddEndPoint);
            postRequest.setConfig( RequestConfig.DEFAULT);
            StringEntity stringEntity = new StringEntity(requestBody, Consts.UTF_8);
            stringEntity.setContentType("application/json");
            postRequest.setEntity(stringEntity);
            CloseableHttpResponse httpResponse = httpClient.execute(postRequest);
            // 获取响应消息实体
            HttpEntity entityRep = httpResponse.getEntity();
            String responseContent = EntityUtils.toString(entityRep, Consts.UTF_8);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                JSONObject jsonObject = JSON.parseObject(responseContent);
                String msg = jsonObject.getString("msg");
                if (jsonObject.getBoolean("success")) {
                    log.debug("代理注册会员快速注册成功,会员中心返回："+msg);
                }else{
                    log.error("代理注册会员快速注册失败,会员中心返回："+msg);
                    throw new BizException("快速注册会员信息保存失败,,会员中心返回"+msg);
                }
            }else{
                log.error("会员中心调用失败,服务器未正常响应:"+responseContent);
                throw new BizException("会员中心调用失败,服务器未正常响应:"+responseContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("快速注册成功,并向MIC发送新增会员信息请求异常："+e.getMessage());
           // throw new BizException("快速注册会员中心处理失败,返回"+e.getMessage());
        }
        //写入到MIC完成
        //发送注册成功系统消息
        sysMessageService.sendMessage(record.getMemberId(), EnumSysMessage.Enum_Message_Register_Success);
        return record;
    }
    /**
     * 会员登录
     * @param account 会员账号（手机号、邮箱、用户名、会员代号）
     * @param password 密码
     * @param findPwdUrl 找回密码URL
     * @return MbMember 会员信息
     * @throws BizException
     */
    public MbMember login(String account, String password, String findPwdUrl) throws BizException {
        //手机号码/用户名/会员代码/邮箱 均可以作为登录账户
        MbMemberQuery memberQuery = new MbMemberQuery();
        memberQuery.setAccount(account);
        /*memberQuery.setPassword(MD5.getMD5(password));//密码*/
        memberQuery.setDelFlg("0");//未被删除
        List<MbMember> memberList = getMapper().queryMbmember(memberQuery);
        if (memberList == null || memberList.size() < 1){
            throw new BizException("会员账户不存在！");
        }
        //验证登录用户状态
        MbMember mbMember = memberList.get(0);
        validLoginMember(mbMember);
        Date today = new Date();
        //检查登录密码
        if (!MD5.getMD5(password).equals(mbMember.getPassword())){
            //登录失败
            int failCount = 0;
            if (mbMember.getLoginDate() != null &&
                    DateUtils.isSameDay(today, mbMember.getLoginDate())){
                if (mbMember.getLoginFailureCount() != null){//当日失败次数加一
                    failCount = mbMember.getLoginFailureCount() + 1;
                }
            }

            //当日登录失败次数，超出最大失败次数
            if (failCount >= maxLoginFailedCount){
//                getLogger().warn(String.format("目前版本超过最大次数不锁定账户,如果看到此信息,说明当日已超过最大次数!"));
                //当日登录失败次数，超过最大失败次数，锁定会员账户
                MbMember member = new MbMember();
                member.setMemberId(mbMember.getMemberId());
                member.setLoginFailureCount(0);
                member.setIsAccountLocked("1");
                member.setLoginDate(today);//用于判断是哪一天
                updateSelective(member);
//                发送账户被锁定系统消息
                getLogger().info("用户帐户【"+member.getMemberCode()+"】被锁定");
                sysMessageService.sendMessage(mbMember.getMemberId(), EnumSysMessage.Enum_Message_Too_Many_Login_Failed);
                throw new BizException("登录失败"+maxLoginFailedCount+"次，会员账户被锁定"+ accountLockMinute +"分钟！");
            } else {
                //当日登录失败次数不超过最大失败次数，记录失败次数
                MbMember member = new MbMember();
                member.setMemberId(mbMember.getMemberId());
                member.setLoginFailureCount(failCount);
                member.setLoginDate(today);//用于判断是哪一天
                updateSelective(member);

                if (failCount >= maxLoginFailedCount - 1){
                    throw new BizException("登录密码不正确！" +  ( maxLoginFailedCount - failCount )+"次错误后，账户将被锁定"+ accountLockMinute +"分钟！<a style='color:blue' href='"+findPwdUrl+"'>找回密码</a>");
                }else if (failCount >= maxLoginFailedCount - 4){
                    throw new BizException("登录密码不正确！您是否需要<a style='color:blue' href='"+findPwdUrl+"'>找回密码</a>?" );
                }

            }

            throw new BizException("登录密码不正确！");
        }
        //登录成功，记录登录日期，登录失败次数设为0
        MbMember member = new MbMember();
        member.setMemberId(mbMember.getMemberId());
        member.setLoginFailureCount(0);
        member.setLoginDate(today);
        updateSelective(member);
        return mbMember;
    }
    /**
     * 会员登录
     * @param account 会员账号（手机号、邮箱、用户名、会员代号）
     * @param password 密码
     * @return MbMember 会员信息
     * @throws BizException
     */
    @Override
    public MbMember login(String account, String password) throws BizException {
        String findPwdUrl = appServerBroker.get("/member/find_pwd1.htm").toString();
        return this.login(account, password, findPwdUrl);
    }
    /**
     * 根据条件查询会员信息
     * @param query
     * @return
     */
    @Override
    public List<MbMember> queryMbmember(MbMemberQuery query){
        return getMapper().queryMbmember(query);
    }
    /**
     * 根据条件分页查询会员信息
     * @param query
     * @param pageInfo
     * @return
     */
    @Override
    public PageInfo<MbMember> queryMbmember(MbMemberQuery query, PageInfo<?> pageInfo){
        PageUtils.page(pageInfo);
        return new PageInfo<MbMember>(getMapper().queryMbmember(query));
    }
    /**
     * 根据条件统计会员数量
     * @param query
     * @return
     */
    @Override
    public int countMbmember(MbMemberQuery query) {
        return getMapper().countMbmember(query);
    }
    /**
     * 更新会员信息
     */
   // @Transactional("member-transactionManager")
    @Override
    public MbMember updateSelective(MbMember member) {
        validRegisterMember(member);
        if (StringUtils.isNotBlank(member.getPassword())){
            //修改密码时，密码加密
            member.setPassword(MD5.getMD5(member.getPassword()));
        }
        member.setUpdateTime(new Date());

        //写入到MIC
        log.debug("更新会员信息,并向MIC发送更新会员信息请求...");
        CloseableHttpClient httpClient=null;
        try {

            MbMember mb=  getMapper().selectByPrimaryKey(member.getMemberId());
            if (mb == null) {
                throw new BizException("根据会员编号查询会员信息失败,会员可能不存在!");
            }
            member = super.updateSelective(member);

            httpClient = createHttpClient();
            JSONObject recordJsonObject =(JSONObject) JSON.toJSON(member);
            if (StringUtils.isNotBlank(member.getUserName())) {
                recordJsonObject.put("loginName", member.getUserName());//登陆名称
            }
            recordJsonObject.put("uid", member.getMemberId());
            recordJsonObject.put("createSource", micApplicationId);
            recordJsonObject.put("updateSource", micApplicationId);
            String requestBody = recordJsonObject.toJSONString();

            log.debug("MIC请求:{}发送\n{}\n:" , micMemberChangeEndPoint,requestBody);
            HttpPost postRequest = new HttpPost(micMemberChangeEndPoint);
            postRequest.setConfig( RequestConfig.DEFAULT);
            StringEntity stringEntity = new StringEntity(requestBody, Consts.UTF_8);
            stringEntity.setContentType("application/json");
            postRequest.setEntity(stringEntity);
            CloseableHttpResponse httpResponse = httpClient.execute(postRequest);
            // 获取响应消息实体
            HttpEntity entityRep = httpResponse.getEntity();
            String responseContent = EntityUtils.toString(entityRep, Consts.UTF_8);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                JSONObject jsonObject = JSON.parseObject(responseContent);
                String msg = jsonObject.getString("msg");
                if (jsonObject.getBoolean("success")) {
                    log.debug("更新会员信息成功,会员中心返回："+msg);
                }else{
                    log.error("更新会员信息失败,会员中心返回："+msg);
                    throw new BizException("会员信息保存失败,,会员中心返回"+msg);
                }
            }else{
                log.error("会员中心调用失败,服务器未正常响应:"+responseContent);
                throw new BizException("会员中心调用失败,服务器未正常响应:"+responseContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("更新会员信息,并向MIC发送更新会员信息请求异常："+e.getMessage());
        }
        //写入到MIC完成
        return member;
    }

    /**
     * 验证会员信息是否符合注册要求
     * @param record
     * @return
     */
    private void validRegisterMember(MbMember record){
        //判断手机号码是否已被注册
        MbMemberQuery memberQuery = new MbMemberQuery();
        memberQuery.setNotMemberId(record.getMemberId());
        memberQuery.setDelFlg("0");
        if(StringUtils.isNotBlank(record.getMobile())){
            memberQuery.setAccount(record.getMobile());
            int count = this.countMbmember(memberQuery);
            if (count > 0){
                throw new BizException("手机号码已被注册！");
            }
        }
        if(StringUtils.isNotBlank(record.getEmail())){
            memberQuery.setAccount(record.getEmail());
            int count = this.countMbmember(memberQuery);
            if (count > 0){
                throw new BizException("邮箱已被注册！");
            }
        }
        if(StringUtils.isNotBlank(record.getUserName())){
            memberQuery.setAccount(record.getUserName());
            int count = this.countMbmember(memberQuery);
            if (count > 0){
                throw new BizException("用户名已被注册！");
            }
        }
    }

    /**
     * 验证登录会员信息
     * @param record
     */
    private void validLoginMember(MbMember record){
        /*//未激活账户在页面处理
        if(!"1".equals(record.getIsActive())){
            throw new BizException("账户尚未激活！");
        }
        */
        if ("1".equals(record.getIsForbidden())){
            throw new BizException("账户已被禁用！");
        } else if ("1".equals(record.getIsAccountLocked())){
            if (record.getLoginDate() != null){
                //若为登录失败次数过多被锁定，足够时间后，自动解锁
                Calendar today = Calendar.getInstance();
                today.setTime(new Date());
                today.add(Calendar.MINUTE, - accountLockMinute);
                if (today.getTime().after(record.getLoginDate())){
                    MbMember member = new MbMember();
                    member.setMemberId(record.getMemberId());
                    member.setLoginFailureCount(0);
                    member.setIsAccountLocked("0");
                    updateSelective(member);
                    return;
                }
            }
            throw new BizException("您的账号已被冻结，请联系客服热线：4008-158-998。");
        }
    }
    /**
    * 取到充值密码密钥
    * @param memberId
    *      会员编号
    * @return
    */
    @Override
    public String getRecoverKey(String memberId){
        String key = uuidGenerator.gain();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MINUTE, Key_Lost_Time);//指定时间以后
        MbMember member = new MbMember();
        member.setMemberId(memberId);
        member.setPasswordRecoverKey(key);
        member.setKeyLostTime(cal.getTime());
        getMapper().updateByPrimaryKeySelective(member);
        return key;
    }
    /**
     * 根据密钥重置密码
     * @param recoverKey
     *      密钥
     * @param password
     *      新密码
     */
    @Override
    public void resetPwd(String recoverKey, String password){
        if(StringUtils.isBlank(recoverKey)){
            throw new BizException("请先验证登录账户！");
        }
        if(StringUtils.isBlank(password)){
            throw new BizException("密码不能为空！");
        }
        MbMemberQuery query = new MbMemberQuery();
        query.setPasswordRecoverKey(recoverKey);
        List<MbMember> memberList = queryMbmember(query);
        if (memberList == null || memberList.size() <= 0){
            throw new BizException("验证不存在或已失效，请先验证账户！");
        }
        MbMember member = memberList.get(0);
        //判断密钥是否过期
        Date lostTime = member.getKeyLostTime();
        String md5Password = MD5.getMD5(password);
        if (new Date().after(lostTime)){
            throw new BizException("验证已过期，请重新验证！");
        }else if (md5Password.equals(member.getPassword())){
            throw new BizException("密码不能与旧密码相同！");
        }
        //重置密码
        String memberId = member.getMemberId();
        MbMember mbMember = new MbMember();
        mbMember.setMemberId(memberId);
        mbMember.setPassword(password);
        mbMember.setIsAccountLocked("0");
        updateSelective(mbMember);
    }
    /**
     * 根据用户编号、旧密码修改密码
     * @param memberId
     * @param oldPassword
     * @param password
     */
    @Override
    public void resetPwd(String memberId, String oldPassword, String password) {
        if(StringUtils.isBlank(oldPassword)){
            throw new BizException("旧密码不能为空！");
        }
        if(StringUtils.isBlank(password)){
            throw new BizException("密码不能为空！");
        }
        // 判断原密码是否正确
        MbMemberQuery query = new MbMemberQuery();
        query.setMemberId(memberId);
        query.setPassword(MD5.getMD5(oldPassword));
        int count = countMbmember(query);
        if (count < 1) {
            throw new BizException("原密码输入不正确！");
        }
        if (oldPassword.equals(password)){
            throw new BizException("密码不能与旧密码相同！");
        }
        // 修改为新密码
        MbMember member = new MbMember();
        member.setMemberId(memberId);
        member.setPassword(password);
        member.setUpdateUser(memberId);
        member.setIsAccountLocked("0");
        updateSelective(member);
    }
    /**
     * 会员代注册账户激活
     * @param mobile
     *      手机号码
     * @param password
     *      新密码
     */
    @Override
    public void memberActive(String mobile, String password){
        MbMemberQuery query = new MbMemberQuery();
        query.setMobile(mobile);
        query.setIsActive("0");
        List<MbMember> memberList = queryMbmember(query);
        if (memberList == null || memberList.size() <= 0){
            throw new BizException("账户不存在！");
        }
        MbMember member = memberList.get(0);
        //设置密码，激活账户
        MbMember mbMember = new MbMember();
        mbMember.setMemberId(member.getMemberId());
        mbMember.setPassword(password);
        mbMember.setIsActive("1");
        updateSelective(mbMember);
    }

    /**
    * om项目***********************************************

    /**
     * 验证 userName，mobile，email 是否存在
     * @param attrName 会员属性名称（userName，mobile，email）
     * @param attrValue
     * @return Mbmember表存在时返回 true
     */
    @Override
    public boolean validateMemberAttr(String attrName,String attrValue) {
        Map<String,Object> map =new HashMap<String,Object>();
        map.put("delFlg", "0");
        map.put(attrName,attrValue);
        int count = getMapper().countMbmemberWithMap(map);
        if(count>0){
            return true;
        }
        return false;
    }

    /**
     * 删除会员信息(非物理删除)
     * @param memberId 主键
     * @return
     */
    @Override
    public int delByMemberId(String memberId) {
        MbMember member = new MbMember();
        member.setMemberId(memberId);
        member.setDelFlg("1");
        return getMapper().updateByPrimaryKeySelective(member);
    }

    /**
     * 批量删除会员(非物理删除)
     * @param memberIds 主键（逗号分割）
     * @return
     */
    @Override
    public int delByMembersIds(String memberIds) {
        int res = 0 ;
        for (String memberId :memberIds.split(",")){
            res +=delByMemberId(memberId);
        }
        return res;
    }
    /**
     * 根据手机号获取到会员信息
     * @param mobile
     * @return
     */
    @Override
    public MbMember getMemberByMobile(String mobile) {
        MbMemberQuery query = new MbMemberQuery();
        query.setMobile(mobile);
        List<MbMember> memberList = queryMbmember(query);
        if (memberList == null || memberList.size() <= 0){
            getLogger().error("No record exists with mobile " + mobile);
            throw new BizException("账户不存在！");
        }
        MbMember member = memberList.get(0);
        return member;
    }
    
    /**
     * 根据memberId获取到会员信息
     * @param memberId
     * @return
     */
    @Override
	public MbMember getMemberByMemberId(String memberId) {
    	 MbMemberQuery query = new MbMemberQuery();
         if(StringUtils.isNotBlank(memberId)) memberId = memberId.replace("OLDSH_","");
         query.setMemberId(memberId);
         List<MbMember> memberList = queryMbmember(query);
         if (memberList == null || memberList.size() <= 0){
             throw new BizException("账户不存在！");
         }
         MbMember member = memberList.get(0);
         return member;
	}

    /**
     * 根据ID array查询集合
     */
	@Override
    public List<MbMember> findByIDArray(String[] arr){
        return getMapper().findByIDArray(arr);
    }


    /**
     * 根据关键字搜索member
     * @param keywords (mobile, name)
     * @return
     */
    @Override
    public PageInfo<MbMember> findByKeywords(PageInfo<?> pageInfo, String keywords){
        PageUtils.page(pageInfo);
        List<MbMember> list = getMapper().findByKeywords(keywords);
        return new PageInfo<MbMember>(list);
    }

    /**
     * 验证会员邮箱信息是否唯一
     * @param memberId 用户ID
     * @param email 邮箱
     * @return
     */
    public void validMemberWithEmail(String memberId,String email){
        MbMember record = new MbMember();
        record.setEmail(email);
        record.setMemberId(memberId);
        this.validRegisterMember(record);
    }

    /**
     * new 查询业务员  2017年04月28日09:57:28
     * @param enterpriseId
     * @return
     */
    public List<MbMember> findSalesMen(String enterpriseId){
        return this.getMapper().findSalesMen(enterpriseId);
    } 
	/**
	 * 根据list里的memberId查询所有用户信息
	 */
	@Override
	public List<MbMember> selectDrawMemberList(List<MbMember> list) {
		return this.getMapper().selectDrawMemberList(list);
	}
	
	/**
     * 第三方会员登录
     * @param memberId 会员账号（手机号、邮箱、用户名、会员代号）
     * @return MbMember 会员信息
     * @throws BizException
     */
    @Override
    public MbMember thirdPartyLogin(String memberId) throws BizException {

        MbMember mbMember = null;
        MbMemberQuery memberQuery = new MbMemberQuery();
        memberQuery.setMemberId(memberId);
        memberQuery.setDelFlg("0");//未被删除
        List<MbMember> memberList = getMapper().queryMbmember(memberQuery);
        if (memberList == null || memberList.size() < 1) {
            throw new BizException("会员账户不存在！");
        }
        //验证登录用户状态
        mbMember = memberList.get(0);
        validLoginMember(mbMember);
        Date today = new Date();

        //登录成功，记录登录日期，登录失败次数设为0
        MbMember member = new MbMember();
        member.setMemberId(mbMember.getMemberId());
        member.setLoginFailureCount(0);
        member.setLoginDate(today);
        updateSelective(member);

        return mbMember;
    }
	

    //查询每日新增用户数量
    public int findNewAddUser(String startDate){
        Map<String,String> param = new HashMap<String,String>();
        param.put("startDate",startDate+" 00:00:00");
        param.put("endDate",startDate+" 23:59:59");
        return this.getMapper().findNewAddUser(param);
    }


    //登录用户统计
    @Override
    public List<UserLoginFormsInfo> getUserLoginForms(String startDate, String endDate){
        Map<String,String> param = new HashMap<String,String>();
        param.put("startDate",startDate);
        param.put("endDate",endDate);
        return this.getMapper().getUserLoginForms(param);
    }

    //会员情况一览表
    @Override
    public List<MemberShipStatisticsBean> getListOfMemberShip(Map<String,String> param) {
        return this.getMapper().getListOfMemberShip(param);
    }

    //获取每日用户注册数
    @Override
    public List<HashMap> getDailyMemberCount(Map<String, Object> param) {
        return this.getMapper().getDailyMemberCount(param);
    }
    //获取每日企业注册数
    @Override
    public List<HashMap> getDailyEnterpriseCount(Map<String, Object> param) {
        return this.getMapper().getDailyEnterpriseCount(param);
    }

    //获取每月用户注册数
    @Override
    public List<HashMap> getMonthMemberCount(Map<String, Object> param) {
        return this.getMapper().getMonthMemberCount(param);
    }
    //获取每月企业注册数
    @Override
    public List<HashMap> getMonthEnterpriseCount(Map<String, Object> param) {
        return this.getMapper().getMonthEnterpriseCount(param);
    }

    /**
     * 创建HTTP连接请求
     * @return
     * @throws Exception
     */
    protected CloseableHttpClient createHttpClient() throws Exception {
        SSLContextBuilder sslContextbuilder = new SSLContextBuilder();
        SSLContext sslContext = sslContextbuilder.loadTrustMaterial(null, new TrustStrategy() {
            // 信任所有证书
            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }
        }).build();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.INSTANCE).register("https", new SSLConnectionSocketFactory(sslContext, new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        }
        )).build();
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        SocketConfig socketConfig = SocketConfig.custom().setTcpNoDelay(true).build();
        connManager.setDefaultSocketConfig(socketConfig);
        MessageConstraints messageConstraints = MessageConstraints.custom().setMaxHeaderCount(200).setMaxLineLength(2000).build();
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setMalformedInputAction(CodingErrorAction.IGNORE).setUnmappableInputAction(CodingErrorAction.IGNORE).setCharset(Consts.UTF_8).setMessageConstraints(messageConstraints).build();
        connManager.setDefaultConnectionConfig(connectionConfig);
        connManager.setMaxTotal(200);
        connManager.setDefaultMaxPerRoute(20);
        CloseableHttpClient closeableHttpClient = HttpClients.custom().disableRedirectHandling().setConnectionManager(connManager).setUserAgent("httpclient").build();
        return closeableHttpClient;
    }


    public int getMemberInfoByMobile(String mobile){
        int result = 0;
        result = this.getMapper().getMemberInfoByMobile(mobile);
        return result;
    }


    /**
     * 根据topic 修改基本信息，不同步
     * @param bean
     */
    @Override
    public void updateAsynchrony(MbMember bean){
        getMapper().updateByPrimaryKeySelective(bean);
    }

}
