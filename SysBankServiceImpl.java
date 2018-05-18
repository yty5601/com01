package com.sinochem.member.biz.impl;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sinochem.common.model.cache.SysBankCache;
import com.sinochem.common.service.BankCacheService;
import com.sinochem.common.service.SysBankService;
import com.sinochem.common.service.impl.BaseServiceImpl;
import com.sinochem.member.mapper.SysBankMapper;
import com.sinochem.member.model.SysBank;
import com.sinochem.member.model.SysBankExample;

@Service
public class SysBankServiceImpl extends BaseServiceImpl<SysBank, String, SysBankExample, SysBankMapper>
		implements SysBankService {

	@Autowired
	private BankCacheService bankCacheService;
	
	@Override
	public List<SysBank> querySysBank(SysBank query) {
		// TODO Auto-generated method stub
		return null;
	}
	/**
	 * 将银行信息缓存到redis中
	 */
	@PostConstruct
	private void PostConstruct(){
		SysBankExample example = new SysBankExample();
		SysBankExample.Criteria criteria = example.createCriteria();
		criteria.andDelFlgEqualTo("0");
		List<SysBank> sysBanks = this.getMapper().selectByExample(example);
		List<SysBankCache> bankCacheList = new ArrayList<SysBankCache>();
		if(sysBanks != null){
			for(SysBank b : sysBanks){
				SysBankCache cache = new SysBankCache();
				cache.setBankId(b.getBankId());
				cache.setBankCode(b.getBankCode());
				cache.setBankName(b.getBankName());
				cache.setLogo(b.getLogo());

				bankCacheList.add(cache);
			}

			bankCacheService.addBankCacheList(bankCacheList);
		}
	}
}
