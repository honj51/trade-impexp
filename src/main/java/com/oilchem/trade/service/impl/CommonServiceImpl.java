package com.oilchem.trade.service.impl;

import com.oilchem.trade.config.Message;
import com.oilchem.trade.util.FileUtil;
import com.oilchem.trade.util.QueryUtils;
import com.oilchem.trade.util.ZipUtil;
import com.oilchem.trade.dao.*;
import com.oilchem.trade.dao.map.AbstractTradeDetailRowMapper;
import com.oilchem.trade.dao.map.MyRowMapper;
import com.oilchem.trade.domain.*;
import com.oilchem.trade.domain.abstrac.TradeDetail;
import com.oilchem.trade.domain.abstrac.TradeSum;
import com.oilchem.trade.domain.abstrac.IdEntity;
import com.oilchem.trade.service.CommonService;
import com.oilchem.trade.bean.DetailCriteria;
import com.oilchem.trade.bean.YearMonthDto;
import jxl.Sheet;
import jxl.Workbook;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

import static com.oilchem.trade.config.Config.*;
import static com.oilchem.trade.config.MapperConfig.*;

/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 12-11-5
 * Time: 下午5:42
 * To change this template use File | Settings | File Templates.
 */
@Service("commonService")
public class CommonServiceImpl implements CommonService {

    @Resource
    CityDao cityDao;
    @Resource
    CompanyTypeDao companyTypeDao;
    @Resource
    CountryDao countryDao;
    @Resource
    CustomsDao customsDao;
    @Resource
    TradeTypeDao tradeTypeDao;
    @Resource
    TransportationDao transportationDao;
    @Resource
    ProductTypeDao productTypeDao;
    @Resource
    LogDao logDao;

    //日志记录器
    Logger logger = LoggerFactory.getLogger(getClass());


    /**
     * 上传文件
     *
     * @param file         MultipartFile的文件
     * @param realDir      目标目录的物理路径
     * @param yearMonthDto
     * @return 返回上传之后文件的url
     * @author wei.luo
     * @createTime 2012-11-7
     */
    public String uploadFile(MultipartFile file, String realDir, YearMonthDto yearMonthDto) {
        if (file == null || StringUtils.isBlank(realDir)) return null;

        String fileUrl = FileUtil.upload(file, realDir, ROOT_URL);
        return fileUrl;
    }

    /**
     * 解包
     *
     * @param logEntry
     * @param unPackageDir 解压目录
     * @return 解压后的文件路径
     */
    //@Before加锁
    //@After解锁
    public String unpackageFile(Map.Entry<Long, Log> logEntry, String unPackageDir) {

        if (logEntry == null || StringUtils.isBlank(unPackageDir))
            return null;

        String uploadPath = logEntry.getValue().getUploadPath();
        String type = FileUtil.getFileSuffix(uploadPath);

        //判断文件类型
        if (type.equals(".zip")) {
            return ZipUtil.unZip(uploadPath, unPackageDir, null);
        } else if (type.equals(".rar")) {
            return ZipUtil.unRar(uploadPath, unPackageDir);
        } else return null;
    }

    /**
     * 导入查询条件表
     *
     * @param sql
     * @param accessPath
     * @return
     */
    @Transactional
    public void
    importCriteriaTab(String sql, String accessPath) {
        if (StringUtils.isBlank(sql) || StringUtils.isBlank(accessPath))
            return;

        ApplicationContext ctx = AppContextManager.getAppContext();
        List<DetailCriteria> detailCriteriaList = new ArrayList<DetailCriteria>();

        try {
            //城市
            DetailCriteria cityCri = new DetailCriteria(
                    CITY,
                    City.class,
                    CityDao.class,
                    CityDao.class.getDeclaredMethod("findByCity", String.class),
                    ctx.getBean(CityDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(cityCri);

            //国家
            DetailCriteria countryCri = new DetailCriteria(
                    COUNTRY,
                    Country.class,
                    CountryDao.class,
                    CountryDao.class.getDeclaredMethod("findByCountry", String.class),
                    ctx.getBean(CountryDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(countryCri);

            //企业性质
            DetailCriteria companyTypeCri = new DetailCriteria(
                    COMPANY_TYPE
                    , CompanyType.class,
                    CompanyTypeDao.class,
                    CompanyTypeDao.class.getDeclaredMethod("findByCompanyType", String.class),
                    ctx.getBean(CompanyTypeDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(companyTypeCri);

            //海关
            DetailCriteria customsCri = new DetailCriteria(
                    CUSTOMS,
                    Customs.class,
                    CustomsDao.class,
                    CustomsDao.class.getDeclaredMethod("findByCustoms", String.class),
                    ctx.getBean(CustomsDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(customsCri);

            //贸易类型
            DetailCriteria tradeTypeCri = new DetailCriteria(
                    TRADE_TYPE,
                    TradeType.class,
                    TradeTypeDao.class,
                    TradeTypeDao.class.getDeclaredMethod("findByTradeType", String.class),
                    ctx.getBean(TradeTypeDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(tradeTypeCri);

            //运输方式
            DetailCriteria transportationCri = new DetailCriteria(
                    TRANSPORTATION,
                    Transportation.class,
                    TransportationDao.class,
                    TransportationDao.class.getDeclaredMethod("findByTransportation", String.class),
                    ctx.getBean(TransportationDao.class),
                    new HashSet<String>());
            detailCriteriaList.add(transportationCri);
        } catch (NoSuchMethodException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

        //匹配
        queryCriteriaRecord(detailCriteriaList, sql, accessPath);

        //导入
        cityDao.save(nameList2IdEntityList(detailCriteriaList.get(0).getRetName(), City.class));
        countryDao.save(nameList2IdEntityList(detailCriteriaList.get(1).getRetName(), Country.class));
        companyTypeDao.save(nameList2IdEntityList(detailCriteriaList.get(2).getRetName(), CompanyType.class));
        customsDao.save(nameList2IdEntityList(detailCriteriaList.get(3).getRetName(), Customs.class));
        tradeTypeDao.save(nameList2IdEntityList(detailCriteriaList.get(4).getRetName(), TradeType.class));
        transportationDao.save(nameList2IdEntityList(detailCriteriaList.get(5).getRetName(), Transportation.class));

    }

    /**
     * name list 到 实例类 list的转换
     *
     * @param nameSet
     * @param idEntityClass
     * @param <E>
     * @return
     */
    private <E extends IdEntity> List<E>
    nameList2IdEntityList(Set<String> nameSet, Class<E> idEntityClass) {
        List<E> idEntityList = new ArrayList<E>();
        for (String name : nameSet) {
            try {
                idEntityList.add(idEntityClass.getConstructor(String.class).newInstance(name));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
        return idEntityList;
    }


    /**
     * 导入明细数据
     *
     * @param repository
     * @param tradeDetailDao
     * @param tradeDetailMapper tradeDetailMapper
     * @param yearMonthDto
     * @param accessPath
     * @param sql               sql      @return  @author wei.luo
     * @param detailClz         明细抽象类
     */
    public <E extends TradeDetail, T extends AbstractTradeDetailRowMapper>
    void importTradeDetail(
            CrudRepository repository, BaseDao<E> tradeDetailDao,
            T tradeDetailMapper, YearMonthDto yearMonthDto,
            String accessPath, String sql, Class detailClz) {

        if (tradeDetailDao == null || tradeDetailMapper == null
                || yearMonthDto == null || StringUtils.isBlank(sql)) return;

        List<E> tradeDetailList = getListFormDB(
                tradeDetailMapper, yearMonthDto, accessPath, sql, detailClz);
        repository.save(tradeDetailList);

    }


    /**
     * 导入Excel
     *
     * @param repository
     * @param tradeSumDao
     * @param logEntry
     * @param tradeSumClass       tradeSum Class
     * @param tradeSumRowMapClass tradeSumRowMap Class
     * @param yearMonthDto        @return 成功或失败
     */
    public <E extends TradeSum, M extends MyRowMapper<E>>
    Boolean importExcel(CrudRepository repository,
                        BaseDao<E> tradeSumDao,
                        Map.Entry<Long, Log> logEntry,
                        Class<E> tradeSumClass,
                        Class<M> tradeSumRowMapClass,
                        YearMonthDto yearMonthDto) {
        if (tradeSumDao == null || tradeSumClass == null
                || tradeSumRowMapClass == null || yearMonthDto == null
                || logEntry == null)
            return null;

        Boolean isSuccess = true;

        //excel取数据
        List<E> tradeSumList = getListFromExcel(logEntry,
                tradeSumClass, tradeSumRowMapClass, yearMonthDto);
        isSuccess = isSuccess && (tradeSumList != null && !tradeSumList.isEmpty());

        //保存数据
        isSuccess = isSuccess && repository.save(tradeSumList) != null;

        return isSuccess;
    }

    /**
     * 获得未解压的文件列表
     *
     * @param tableType@return 返回记录的Id与包的全路径组成的Map
     */
    public Map<Long, Log> getUnExtractPackage(String tableType) {
        if (tableType == null) return null;

        Method findByMethod = null;
        try {

            findByMethod = LogDao.class.getDeclaredMethod(
                    "findByExtractFlagAndTableType", String.class, String.class);

        } catch (NoSuchMethodException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return getLogMap(tableType, UNEXTRACT_FLAG,
                findByMethod, Message.FileType.UPLOAD_FILE);
    }

    /**
     * 获得未导入的文件列表
     *
     * @param tableType@return 返回记录的Id与文件的全路径组成的Map
     */
    public Map<Long, Log> getUnImportFile(String tableType) {
        if (StringUtils.isBlank(tableType)) return null;
        Method findByMethod = null;
        try {

            findByMethod = LogDao.class.getDeclaredMethod(
                    "findByImportFlagAndTableType", String.class, String.class);

        } catch (NoSuchMethodException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return getLogMap(tableType, UNIMPORT_FLAG,
                findByMethod, Message.FileType.IMPORT_FILE);
    }

    /**
     * 获得logMap
     *
     * @param tableType
     * @param process_flag
     * @param findByMethod
     * @param fileType
     * @return
     * @throws Exception
     */
    private Map<Long, Log> getLogMap(String tableType, String process_flag,
                                     Method findByMethod, Message.FileType fileType) {

        if (StringUtils.isBlank(tableType) || StringUtils.isBlank(process_flag))
            return null;

        Map<Long, Log> packaeMap = new HashMap<Long, Log>();
        List<Log> logList = null;
        Object obj = null;

        //查找操作
        try {
            if (tableType.equals(DETAIL)) {
                obj = findByMethod.invoke(logDao, process_flag, DETAIL);
            } else if (tableType.equals(SUM)) {
                obj = findByMethod.invoke(logDao, process_flag, SUM);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

        if (obj != null)
            logList = (List<Log>) obj;

        //把记录放到map中
        if (logList != null && !logList.isEmpty()) {
            for (Log log : logList) {
                if (fileType.equals(Message.FileType.UPLOAD_FILE))
                    packaeMap.put(log.getId(), log);
                if (fileType.equals(Message.FileType.IMPORT_FILE))
                    packaeMap.put(log.getId(), log);
            }
        }

        return packaeMap;
    }

    /**
     * 获得数据模型的数据列表
     *
     *
     * @param daoClass daoClass
     * @param idEntityName
     * @return
     */
    public <T extends IdEntity> List<T> findAllIdEntityList(
            Class daoClass, String idEntityName) {
        if (daoClass == null) return null;

        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, idEntityName));

        List<T> idEntityList = null;
        Object t = ContextLoader.getCurrentWebApplicationContext().getBean(daoClass);
        try {
            Object obj = daoClass.getMethod("findAll",Sort.class).invoke(t,sort);
            if (obj != null) {
                idEntityList = (List<T>) obj;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return idEntityList;
    }

    /**
     * 传入这个字段作为条件查询
     *
     * @param fieldName  java对象中字段的名
     * @param fieldValue 字段的值
     * @param <T>
     * @return
     */
    public <T extends TradeDetail> Specification<T> hasField(
            String fieldName, String fieldValue) {
        return newSpecHasField(fieldName, fieldValue);
    }

    private <T extends TradeDetail> Specification<T> newSpecHasField(
            final String fieldName, final String fieldValue) {
        return new Specification<T>() {

            @Override
            public javax.persistence.criteria.Predicate
            toPredicate(Root<T> impTradeDetailRoot,
                        CriteriaQuery<?> query, CriteriaBuilder cb) {
                if (fieldValue == null) return null;
                return cb.equal(impTradeDetailRoot.get(fieldName), fieldValue);
            }
        };
    }

    /**
     * 从Access获得过滤后查询条件数据
     *
     * @param detailCriteriaList
     * @param sql
     * @param accessPath
     * @return
     */
    private void
    queryCriteriaRecord(List<DetailCriteria> detailCriteriaList,
                        String sql,
                        String accessPath) {

        if (accessPath == null || detailCriteriaList == null
                || StringUtils.isBlank(sql)) return;

        Connection conn = getDBConnect(accessPath);
        Statement statement = null;
        ResultSet rs = null;

        try {
            statement = conn.createStatement();
            rs = statement.executeQuery(sql);
            while (rs.next()) {

                fillDetailCriteriaList(detailCriteriaList, rs);

            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException();
        } finally {
            closeDBResource(conn, statement, rs);
        }
    }

    /**
     * 从Access表中获得明细数据list
     *
     * @param tradeDetailMapper tradeDetailMapper
     * @param yearMonthDto
     * @param accessPath
     * @param sql               sql
     * @param detailClz         detailClz   @return
     */
    private <E extends TradeDetail, T extends AbstractTradeDetailRowMapper> List<E>
    getListFormDB(T tradeDetailMapper, YearMonthDto yearMonthDto,
                  String accessPath, String sql, Class detailClz) {
        //查出来然后导入
        Connection conn = getDBConnect(accessPath);
        Statement statement = null;
        ResultSet rs = null;
        List<E> tradeDetailList = new ArrayList<E>();
        try {
            statement = conn.createStatement();
            rs = statement.executeQuery(sql);
            while (rs.next()) {

                fillTradeDetailList(rs, tradeDetailMapper, yearMonthDto, detailClz, tradeDetailList);

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            closeDBResource(conn, statement, rs);
        }
        return tradeDetailList;
    }

    /**
     * 填充  tradeDetailList
     *
     * @param rs
     * @param tradeDetailMapper
     * @param yearMonthDto
     * @param detailClz
     * @param tradeDetailList
     * @param <E>
     * @param <T>
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws SQLException
     */
    private <E extends TradeDetail, T extends AbstractTradeDetailRowMapper>
    void fillTradeDetailList(ResultSet rs, T tradeDetailMapper,
                             YearMonthDto yearMonthDto, Class detailClz,
                             List<E> tradeDetailList)
            throws InstantiationException, IllegalAccessException, SQLException {

        E e = (E) detailClz.cast(detailClz.newInstance());
        tradeDetailMapper.setTraddDetail(e, rs,
                yearMonthDto.getYear(), yearMonthDto.getMonth());
        tradeDetailList.add(e);
    }

    /**
     * 填充 detailCriteriaList
     *
     * @param detailCriteriaList
     * @param rs
     * @throws SQLException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private void fillDetailCriteriaList(List<DetailCriteria> detailCriteriaList, ResultSet rs)
            throws SQLException, IllegalAccessException, InvocationTargetException {
        //取出每条记录中的条件字段，与条件表对应
        for (DetailCriteria detailCriteria : detailCriteriaList) {

            String name = rs.getString(detailCriteria.getFieldName());
            if (StringUtils.isBlank(name)) {
                continue;
            }
            Object findByMethodRet = detailCriteria.getFindByMethod()
                    .invoke(detailCriteria.getDao(), name.trim());

            Set<String> nameSet = detailCriteria.getRetName();
            //如果没有找到相同记录，则把name字段保存到IdEntity引用的对象中
            if (findByMethodRet == null) {
                nameSet.add(name.trim());
            }
        }
    }

    /**
     * 获得excel数据中的list
     *
     * @param logEntry
     * @param tradeSumClass
     * @param tradeSumRowMapClass
     * @param yearMonthDto
     * @return
     */
    private <E extends TradeSum, M extends MyRowMapper<E>> List<E>
    getListFromExcel(Map.Entry<Long, Log> logEntry,
                     Class<E> tradeSumClass,
                     Class<M> tradeSumRowMapClass,
                     YearMonthDto yearMonthDto) {

        //待导入的总表记录List
        List<E> tradeSumList = new ArrayList<E>();

        try {
            //从excel中取得eList
            Workbook workbook = Workbook.getWorkbook(
                    new File(logEntry.getValue().getExtractPath()));
            Sheet sheet = workbook.getSheet(0);
            int rows = sheet.getRows();
            int rowIdx = sheet.findCell(PRODUCT_XNAME).getRow() + 1;
            Integer year = yearMonthDto.getYear();
            Integer month = yearMonthDto.getMonth();
            String yearMonth = year + "-" + (month < 10 ? "0" + month : month);

            //遍历excel
            for (; rowIdx < rows; rowIdx++) {
                E tradeSum = tradeSumClass.getConstructor(
                        Integer.class, Integer.class, String.class, String.class)
                        .newInstance(year, month, yearMonth,
                                yearMonthDto.getProductType());

                Constructor<M> constructor = tradeSumRowMapClass.getConstructor(
                        int.class, tradeSumClass, Sheet.class);
                M tradeSumMyRowMapper = constructor.newInstance(rowIdx, tradeSum, sheet);
                tradeSumList.add(tradeSumMyRowMapper.getMappingInstance());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return tradeSumList;
    }

    /**
     * 建立Access连接
     *
     * @param accessPath
     * @return
     */
    private Connection getDBConnect(String accessPath) {
        Connection conn;//连接参数
        Properties prop = new Properties();
        prop.put("charSet", "GBK");
        prop.put("user", "");
        prop.put("password", "");
        String url = "jdbc:odbc:driver={Microsoft Access Driver (*.mdb)};DBQ="
                + accessPath;

        //创建连接
        try {
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            conn = DriverManager.getConnection(url, prop);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return conn;
    }


    /**
     * 关闭statement与ResultSet
     *
     * @param statement
     * @param rs
     */
    private void closeDBResource(
            Connection conn, Statement statement, ResultSet rs) {
        try {
            if (rs == null) {
                rs.close();
            }
            if (statement == null) {
                statement.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException();
        }
    }

}
