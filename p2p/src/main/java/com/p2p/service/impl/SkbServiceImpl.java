package com.p2p.service.impl;

import com.p2p.bean.Hkb;
import com.p2p.bean.Skb;
import com.p2p.bean.Tzb;
import com.p2p.bean.UserMoney;
import com.p2p.calc.*;
import com.p2p.common.Pager;
import com.p2p.common.ServerResponse;
import com.p2p.dao.HkbMapper;
import com.p2p.dao.SkbMapper;
import com.p2p.dao.TzbMapper;
import com.p2p.dao.UserMoneyMapper;
import com.p2p.enums.WayEnum;
import com.p2p.service.SkbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by 7025 on 2017/12/25.
 */
@Service
public class SkbServiceImpl extends AbstractServiceImpl implements SkbService {

    private SkbMapper skbMapper;
    private UserMoneyMapper userMoneyMapper;
    private TzbMapper tzbMapper;
    private HkbMapper hkbMapper;
    private Loan loan;

    @Autowired
    public void setSkbMapper(SkbMapper skbMapper) {
        super.setBaseDAO(skbMapper);
        this.skbMapper = skbMapper;
    }

    @Autowired
    public void setTzbMapper(TzbMapper tzbMapper) {
        this.tzbMapper = tzbMapper;
    }

    @Autowired
    public void setHkbMapper(HkbMapper hkbMapper) {
        this.hkbMapper = hkbMapper;
    }

    @Autowired
    public void setUserMoneyMapper(UserMoneyMapper userMoneyMapper) {
        this.userMoneyMapper = userMoneyMapper;
    }

    @Override
    @Transactional
    public Pager skblist(int pageNo, int pageSize, Integer uid, Integer baid) {
        saveSkb(uid,baid);
        Pager pager = new Pager(pageNo, pageSize);
        pager.setRows(skbMapper.list(pager, uid, baid));
        pager.setTotal(skbMapper.countUid(uid, baid));
        return pager;
    }

    @Transactional
    public ServerResponse saveSkb(Integer uid, Integer baid){
        //装载保存收款表的list
        List<Skb> skbList = new ArrayList<>();
        //根据用户id和收款表的id去查询有没有记录
        Long count = skbMapper.getByUidAndBaid(uid, baid);
        //如果没有记录就生成
        if(count==0) {
            //查询出该用户对该借款投了几次
            List<Tzb> tzbList = tzbMapper.listTzb(uid, baid);
            if(tzbList != null && tzbList.size() > 0) {
                //计算出总投资金额
                BigDecimal money = new BigDecimal(0);
                for (Tzb tzb : tzbList) {
                    money = money.add(tzb.getMoney());
                }
                Tzb tzb = tzbList.get(0);
                if(tzb.getResint2().equals(Integer.valueOf(WayEnum.PAYOFF_ONCE.getCode()))){
                    Skb skb = new Skb();
                    skb.setUid(uid);
                    skb.setBaid(tzb.getBaid());
                    skb.setJuid(tzb.getJuid());
                    skb.setYbx(money.multiply(BigDecimal.valueOf(tzb.getNprofit()/100)).add(money).setScale(2, BigDecimal.ROUND_HALF_UP));
                    skb.setRbx(new BigDecimal(0));
                    skb.setYlx(money.multiply(BigDecimal.valueOf(tzb.getNprofit()/100)).setScale(2, BigDecimal.ROUND_HALF_UP));
                    skb.setRlx(new BigDecimal(0));
                    skb.setYbj(money);
                    skb.setRbj(new BigDecimal(0));
                    skb.setRnum(0);
                    skb.setTnum(1);
                    skb.setBaid(baid);
                    List<Hkb> hkbs = hkbMapper.getSkTime(tzb.getBaid());
                    skb.setSktime(hkbs.get(0).getYtime());
                    skbList.add(skb);
                    skbMapper.saveList(skbList);
                    return ServerResponse.createBySuccess();
                }else if(tzb.getResint2().equals(Integer.valueOf(WayEnum.XIAN_XI.getCode()))){
                    List<Hkb> hkbs = hkbMapper.getSkTime(tzb.getBaid());
                    if(hkbs != null && hkbs.size() > 0) {
                        int suoyin = 0;
                        for(int i=0;i<tzb.getResint1();i++){
                            Skb skb = new Skb();
                            skb.setUid(uid);
                            skb.setBaid(tzb.getBaid());
                            skb.setJuid(tzb.getJuid());
                            skb.setRnum(0);
                            skb.setTnum(tzb.getResint1());
                            skb.setSktime(hkbs.get(suoyin).getYtime());
                            skb.setRbx(new BigDecimal(0));
                            skb.setRlx(new BigDecimal(0));
                            skb.setYlx(money.multiply(BigDecimal.valueOf(tzb.getNprofit()/100/12)).setScale(2, BigDecimal.ROUND_HALF_UP));
                            if(i==tzb.getResint1()-1){
                                skb.setYbx(money.multiply(BigDecimal.valueOf(tzb.getNprofit()/100/12)).add(money).setScale(2, BigDecimal.ROUND_HALF_UP));
                                skb.setYbj(money);
                            }else{
                                skb.setYbx(skb.getYlx());
                                skb.setYbj(new BigDecimal(0));
                            }
                            skbList.add(skb);
                            suoyin+=1;
                        }
                        skbMapper.saveList(skbList);
                        return ServerResponse.createBySuccess();
                    }
                }else  if(tzb.getResint2().equals(Integer.valueOf(WayEnum.EQUAL_BX.getCode()))) {
                    ACPIMLoanCalculator calculator = new ACPIMLoanCalculator();
                    loan = calculator.calLoan(LoanUtil.totalLoanMoney(money, 0), tzb.getResint1(), LoanUtil.rate(tzb.getNprofit(), 1), LoanUtil.RATE_TYPE_YEAR);
                }else if(tzb.getResint2().equals(Integer.valueOf(WayEnum.EQUAL_BJ.getCode()))){
                    ACMLoanCalculator calculator = new ACMLoanCalculator();
                    loan = calculator.calLoan(LoanUtil.totalLoanMoney(money, 0), tzb.getResint1(), LoanUtil.rate(tzb.getNprofit(), 1), LoanUtil.RATE_TYPE_YEAR);
                }
                List<Hkb> hkb = hkbMapper.getSkTime(baid);
                if(hkb != null && hkb.size() > 0) {
                    int suoyin = 0;
                    for (LoanByMonth loanByMonth : loan.getAllLoans()) {
                        Skb skb = new Skb();
                        skb.setUid(uid);
                        skb.setJuid(tzb.getJuid());
                        skb.setBaid(baid);
                        skb.setYbx(loanByMonth.getRepayment());
                        skb.setRbx(new BigDecimal(0));
                        skb.setYlx(loanByMonth.getInterest());
                        skb.setRlx(new BigDecimal(0));
                        skb.setYbj(loanByMonth.getPayPrincipal());
                        skb.setRbj(new BigDecimal(0));
                        skb.setSktime(hkb.get(suoyin).getYtime());
                        skb.setRnum(0);
                        skb.setTnum(tzb.getResint1());
                        skbList.add(skb);
                        suoyin+=1;
                    }
                    skbMapper.saveList(skbList);
                }
            }
        }
        return ServerResponse.createBySuccess();
    }

    @Override
    @Transactional
    public ServerResponse confirm(Skb skb) {
        UserMoney userMoney = userMoneyMapper.getUserMoney(skb.getUid());
        if(skb.getSktime().compareTo(Calendar.getInstance().getTime()) == 1) {
            return ServerResponse.createByError("未到收款时间");
        }
        userMoney.setKymoney(userMoney.getKymoney().add(skb.getYbx()));
        userMoney.setDsmoney(userMoney.getDsmoney().subtract(skb.getYbx()));
        userMoneyMapper.update(userMoney);
        // 更新单个
        skb.setRbj(skb.getYbj());
        skb.setRbx(skb.getYbx());
        skb.setRlx(skb.getYlx());
        skbMapper.update(skb);
        // 更新此标所有
        Skb skb1 = new Skb();
        skb1.setBaid(skb.getBaid());
        skb1.setUid(skb.getUid());
        skb1.setRnum(skb.getRnum() + 1);
        skbMapper.updateByBaid(skb1);
        return ServerResponse.createBySuccess("收款成功");
    }
}
