package cn.iocoder.yudao.module.infra.service.codegen;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.controller.admin.codegen.vo.CodegenUpdateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.codegen.vo.table.CodegenTablePageReqVO;
import cn.iocoder.yudao.module.infra.convert.codegen.CodegenConvert;
import cn.iocoder.yudao.module.infra.dal.dataobject.codegen.CodegenColumnDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.codegen.CodegenTableDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.codegen.SchemaColumnDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.codegen.SchemaTableDO;
import cn.iocoder.yudao.module.infra.dal.mysql.codegen.CodegenColumnMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.codegen.CodegenTableMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.codegen.SchemaColumnMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.codegen.SchemaTableMapper;
import cn.iocoder.yudao.module.infra.enums.codegen.CodegenImportTypeEnum;
import cn.iocoder.yudao.module.infra.framework.codegen.config.CodegenProperties;
import cn.iocoder.yudao.module.infra.service.codegen.inner.CodegenBuilder;
import cn.iocoder.yudao.module.infra.service.codegen.inner.CodegenEngine;
import cn.iocoder.yudao.module.infra.service.codegen.inner.CodegenSQLParser;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import org.apache.commons.collections4.KeyValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

/**
 * ไปฃ็ ็ๆ Service ๅฎ็ฐ็ฑป
 *
 * @author ่้ๆบ็ 
 */
@Service
public class CodegenServiceImpl implements CodegenService {

    @Resource
    private SchemaTableMapper schemaTableMapper;
    @Resource
    private SchemaColumnMapper schemaColumnMapper;
    @Resource
    private CodegenTableMapper codegenTableMapper;
    @Resource
    private CodegenColumnMapper codegenColumnMapper;

    @Resource
    private AdminUserApi userApi;

    @Resource
    private CodegenBuilder codegenBuilder;
    @Resource
    private CodegenEngine codegenEngine;

    @Resource
    private CodegenProperties codegenProperties;

    private Long createCodegen0(Long userId, CodegenImportTypeEnum importType,
                                SchemaTableDO schemaTable, List<SchemaColumnDO> schemaColumns) {
        // ๆ ก้ชๅฏผๅฅ็่กจๅๅญๆฎต้็ฉบ
        if (schemaTable == null) {
            throw exception(CODEGEN_IMPORT_TABLE_NULL);
        }
        if (CollUtil.isEmpty(schemaColumns)) {
            throw exception(CODEGEN_IMPORT_COLUMNS_NULL);
        }
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        if (codegenTableMapper.selectByTableName(schemaTable.getTableName()) != null) {
            throw exception(CODEGEN_TABLE_EXISTS);
        }

        // ๆๅปบ CodegenTableDO ๅฏน่ฑก๏ผๆๅฅๅฐ DB ไธญ
        CodegenTableDO table = codegenBuilder.buildTable(schemaTable);
        table.setImportType(importType.getType());
        table.setAuthor(userApi.getUser(userId).getNickname());
        codegenTableMapper.insert(table);
        // ๆๅปบ CodegenColumnDO ๆฐ็ป๏ผๆๅฅๅฐ DB ไธญ
        List<CodegenColumnDO> columns = codegenBuilder.buildColumns(schemaColumns);
        columns.forEach(column -> {
            column.setTableId(table.getId());
            codegenColumnMapper.insert(column); // TODO ๆน้ๆๅฅ
        });
        return table.getId();
    }

    @Override
    public Long createCodegenListFromSQL(Long userId, String sql) {
        // ไป SQL ไธญ๏ผ่ทๅพๆฐๆฎๅบ่กจ็ปๆ
        SchemaTableDO schemaTable;
        List<SchemaColumnDO> schemaColumns;
        try {
            KeyValue<SchemaTableDO, List<SchemaColumnDO>> result = CodegenSQLParser.parse(sql);
            schemaTable = result.getKey();
            schemaColumns = result.getValue();
        } catch (Exception ex) {
            throw exception(CODEGEN_PARSE_SQL_ERROR);
        }
        // ๅฏผๅฅ
        return this.createCodegen0(userId, CodegenImportTypeEnum.SQL, schemaTable, schemaColumns);
    }

    @Override
    public Long createCodegen(Long userId, String tableName) {
        // ่ทๅๅฝๅschema
        String tableSchema = codegenProperties.getDbSchemas().iterator().next();
        // ไปๆฐๆฎๅบไธญ๏ผ่ทๅพๆฐๆฎๅบ่กจ็ปๆ
        SchemaTableDO schemaTable = schemaTableMapper.selectByTableSchemaAndTableName(tableSchema, tableName);
        List<SchemaColumnDO> schemaColumns = schemaColumnMapper.selectListByTableName(tableSchema, tableName);
        // ๅฏผๅฅ
        return this.createCodegen0(userId, CodegenImportTypeEnum.DB, schemaTable, schemaColumns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createCodegenListFromDB(Long userId, List<String> tableNames) {
        List<Long> ids = new ArrayList<>(tableNames.size());
        // ้ๅๆทปๅ ใ่ฝ็ถๆ็ไผไฝไธ็น๏ผไฝๆฏๆฒกๅฟ่ฆๅๆๅฎๅจๆน้๏ผๅ ไธบไธไผ่ฟไนๅคง้
        tableNames.forEach(tableName -> ids.add(createCodegen(userId, tableName)));
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCodegen(CodegenUpdateReqVO updateReqVO) {
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        if (codegenTableMapper.selectById(updateReqVO.getTable().getId()) == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }

        // ๆดๆฐ table ่กจๅฎไน
        CodegenTableDO updateTableObj = CodegenConvert.INSTANCE.convert(updateReqVO.getTable());
        codegenTableMapper.updateById(updateTableObj);
        // ๆดๆฐ column ๅญๆฎตๅฎไน
        List<CodegenColumnDO> updateColumnObjs = CodegenConvert.INSTANCE.convertList03(updateReqVO.getColumns());
        updateColumnObjs.forEach(updateColumnObj -> codegenColumnMapper.updateById(updateColumnObj));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCodegenFromDB(Long tableId) {
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        CodegenTableDO table = codegenTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        String tableSchema = codegenProperties.getDbSchemas().iterator().next();
        // ไปๆฐๆฎๅบไธญ๏ผ่ทๅพๆฐๆฎๅบ่กจ็ปๆ
        List<SchemaColumnDO> schemaColumns = schemaColumnMapper.selectListByTableName(tableSchema, table.getTableName());

        // ๆง่กๅๆญฅ
        this.syncCodegen0(tableId, schemaColumns);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCodegenFromSQL(Long tableId, String sql) {
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        CodegenTableDO table = codegenTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        // ไป SQL ไธญ๏ผ่ทๅพๆฐๆฎๅบ่กจ็ปๆ
        List<SchemaColumnDO> schemaColumns;
        try {
            KeyValue<SchemaTableDO, List<SchemaColumnDO>> result = CodegenSQLParser.parse(sql);
            schemaColumns = result.getValue();
        } catch (Exception ex) {
            throw exception(CODEGEN_PARSE_SQL_ERROR);
        }

        // ๆง่กๅๆญฅ
        this.syncCodegen0(tableId, schemaColumns);
    }

    private void syncCodegen0(Long tableId, List<SchemaColumnDO> schemaColumns) {
        // ๆ ก้ชๅฏผๅฅ็ๅญๆฎตไธไธบ็ฉบ
        if (CollUtil.isEmpty(schemaColumns)) {
            throw exception(CODEGEN_SYNC_COLUMNS_NULL);
        }
        Set<String> schemaColumnNames = CollectionUtils.convertSet(schemaColumns, SchemaColumnDO::getColumnName);

        // ๆๅปบ CodegenColumnDO ๆฐ็ป๏ผๅชๅๆญฅๆฐๅข็ๅญๆฎต
        List<CodegenColumnDO> codegenColumns = codegenColumnMapper.selectListByTableId(tableId);
        Set<String> codegenColumnNames = CollectionUtils.convertSet(codegenColumns, CodegenColumnDO::getColumnName);
        // ็งป้คๅทฒ็ปๅญๅจ็ๅญๆฎต
        schemaColumns.removeIf(column -> codegenColumnNames.contains(column.getColumnName()));
        // ่ฎก็ฎ้่ฆๅ ้ค็ๅญๆฎต
        Set<Long> deleteColumnIds = codegenColumns.stream().filter(column -> !schemaColumnNames.contains(column.getColumnName()))
                .map(CodegenColumnDO::getId).collect(Collectors.toSet());
        if (CollUtil.isEmpty(schemaColumns) && CollUtil.isEmpty(deleteColumnIds)) {
            throw exception(CODEGEN_SYNC_NONE_CHANGE);
        }

        // ๆๅฅๆฐๅข็ๅญๆฎต
        List<CodegenColumnDO> columns = codegenBuilder.buildColumns(schemaColumns);
        columns.forEach(column -> {
            column.setTableId(tableId);
            codegenColumnMapper.insert(column); // TODO ๆน้ๆๅฅ
        });
        // ๅ ้คไธๅญๅจ็ๅญๆฎต
        if (CollUtil.isNotEmpty(deleteColumnIds)) {
            codegenColumnMapper.deleteBatchIds(deleteColumnIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCodegen(Long tableId) {
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        if (codegenTableMapper.selectById(tableId) == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }

        // ๅ ้ค table ่กจๅฎไน
        codegenTableMapper.deleteById(tableId);
        // ๅ ้ค column ๅญๆฎตๅฎไน
        codegenColumnMapper.deleteListByTableId(tableId);
    }

    @Override
    public PageResult<CodegenTableDO> getCodegenTablePage(CodegenTablePageReqVO pageReqVO) {
        return codegenTableMapper.selectPage(pageReqVO);
    }

    @Override
    public CodegenTableDO getCodegenTablePage(Long id) {
        return codegenTableMapper.selectById(id);
    }

    @Override
    public List<CodegenTableDO> getCodeGenTableList() {
        return codegenTableMapper.selectList();
    }

    @Override
    public List<CodegenColumnDO> getCodegenColumnListByTableId(Long tableId) {
        return codegenColumnMapper.selectListByTableId(tableId);
    }

    @Override
    public Map<String, String> generationCodes(Long tableId) {
        // ๆ ก้ชๆฏๅฆๅทฒ็ปๅญๅจ
        CodegenTableDO table = codegenTableMapper.selectById(tableId);
        if (codegenTableMapper.selectById(tableId) == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        List<CodegenColumnDO> columns = codegenColumnMapper.selectListByTableId(tableId);
        if (CollUtil.isEmpty(columns)) {
            throw exception(CODEGEN_COLUMN_NOT_EXISTS);
        }

        // ๆง่ก็ๆ
        return codegenEngine.execute(table, columns);
    }

    @Override
    public List<SchemaTableDO> getSchemaTableList(String tableName, String tableComment) {
        List<SchemaTableDO> tables = schemaTableMapper.selectList(codegenProperties.getDbSchemas(), tableName, tableComment);
        // TODO ๅผบๅถ็งป้ค Quartz ็่กจ๏ผๆชๆฅๅๆๅฏ้็ฝฎ
        tables.removeIf(table -> table.getTableName().startsWith("QRTZ_"));
        tables.removeIf(table -> table.getTableName().startsWith("ACT_"));
        return tables;
    }

//    /**
//     * ไฟฎๆนไฟๅญๅๆฐๆ ก้ช
//     *
//     * @param genTable ไธๅกไฟกๆฏ
//     */
//    @Override
//    public void validateEdit(GenTable genTable) {
//        if (GenConstants.TPL_TREE.equals(genTable.getTplCategory())) {
//            String options = JSON.toJSONString(genTable.getParams());
//            JSONObject paramsObj = JSONObject.parseObject(options);
//            if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_CODE))) {
//                throw new CustomException("ๆ ็ผ็ ๅญๆฎตไธ่ฝไธบ็ฉบ");
//            } else if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_PARENT_CODE))) {
//                throw new CustomException("ๆ ็ถ็ผ็ ๅญๆฎตไธ่ฝไธบ็ฉบ");
//            } else if (StringUtils.isEmpty(paramsObj.getString(GenConstants.TREE_NAME))) {
//                throw new CustomException("ๆ ๅ็งฐๅญๆฎตไธ่ฝไธบ็ฉบ");
//            }
//        }
//    }

}
