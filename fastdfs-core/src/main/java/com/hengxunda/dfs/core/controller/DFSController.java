package com.hengxunda.dfs.core.controller;

import com.hengxunda.dfs.base.BaseConntroller;
import com.hengxunda.dfs.base.ErrorCode;
import com.hengxunda.dfs.core.entity.AppInfoEntity;
import com.hengxunda.dfs.core.entity.FileInfoEntity;
import com.hengxunda.dfs.core.fastdfs.HttpClient;
import com.hengxunda.dfs.core.service.AppInfoService;
import com.hengxunda.dfs.core.service.FileInfoService;
import com.hengxunda.dfs.utils.MimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.csource.fastdfs.StorageClient1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@RestController
@RequestMapping(value = "/dfs")
public class DFSController extends BaseConntroller {

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private AppInfoService appInfoService;

    /**
     * 上传文件信息
     *
     * @param appKey
     * @param fileName
     * @param fileLength
     * @return
     */
    @RequestMapping(value = "/v1/sUpload")
    public @ResponseBody
    String startUpload(@RequestParam("appKey") String appKey,
                       @RequestParam("fileName") String fileName, @RequestParam("fileLength") Long fileLength) {
        int fileInfoId = -1;
        try {
            fileInfoId = fileInfoService.addFileInfo(appKey, FileInfoEntity.FILE_ACCESS_TYPE_NO_AUTH, fileName,
                    fileLength);
        } catch (Exception e) {
            log.error("add file info error !", e);
        }
        if (fileInfoId > 0) {
            String body = "{\"fileInfoId\":" + fileInfoId + "}";
            return getResponseOKWithBody(body);
        } else {
            return getResponseByCode(ErrorCode.SERVER_ERROR);
        }
    }

    /**
     * 更新fileId
     *
     * @param fileInfoId
     * @param fileId
     */
    @RequestMapping(value = "/v1/eUpload")
    public void endUpload(@RequestParam("fileInfoId") Integer fileInfoId, @RequestParam("fileId") String fileId) {
        // 上传成功执行一些保存fileId与业务数据映射关系的操作
        if (fileId != null) {
            FileInfoEntity fileInfo = new FileInfoEntity();
            int pos = fileId.indexOf(StorageClient1.SPLIT_GROUP_NAME_AND_FILENAME_SEPERATOR);
            if (pos > 0) {
                String groupName = fileId.substring(0, pos);
                fileInfo.setGroupName(groupName);
            }
            fileInfo.setId(fileInfoId);
            fileInfo.setFileId(fileId);
            fileInfo.setUpdateDate(new Date());
            fileInfo.setStatus(FileInfoEntity.FILE_STATUS_UPLOADED);
            fileInfoService.updateFileInfoById(fileInfo);
        }
    }

    /**
     * 获取服务器信息
     *
     * @param appKey
     * @return
     */
    @RequestMapping(value = "/v1/server")
    public @ResponseBody
    String server(@RequestParam("appKey") String appKey) {
        String tracker_servers = HttpClient.getInstance().getTrackersConfig();
        AppInfoEntity app = appInfoService.getAppInfo(appKey);
        String groupName;
        String body;
        if (app != null) {
            groupName = app.getGroupName();
            body = "{\"trackerServers\":\"" + tracker_servers + "\",\"groupName\":\"" + groupName + "\"}";
            return getResponseOKWithBody(body);
        } else {
            return getResponseByCode(ErrorCode.APP_NOT_EXIST);
        }
    }

    /**
     * 下载
     *
     * @param fileId   fastdfs返回的fileId
     * @param direct   是否直接显示，true表示可以直接显示,false表示可以下载保存成文件(默认)
     * @param response
     * @throws IOException
     */
    @RequestMapping(value = "/v1/download", method = RequestMethod.GET)
    public void downloadFile(@RequestParam("fileId") String fileId, boolean direct, HttpServletResponse request,
                             HttpServletResponse response) throws IOException {
        try {
            response.setCharacterEncoding(UTF8);
            response.setHeader("Connection", "close"); // 注意区分大小写
            String fileExtName = FilenameUtils.getExtension(fileId);
            String contextType = MimeUtils.guessMimeTypeFromExtension(fileExtName);
            if (contextType != null) {
                response.setContentType(contextType);
            }
            String fileName = fileInfoService.getFileNameByFileId(fileId);
            if (StringUtils.isBlank(fileName)) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
                StringBuilder sb = new StringBuilder(128);
                fileName = sb.append(format.format(new Date())).append(".").append(fileExtName).toString();
            }
            String userAgent = request.getHeader("User-Agent");
            byte[] bytes;
            if (userAgent != null) {
                bytes = userAgent.contains("MSIE") ? fileName.getBytes() : fileName.getBytes("UTF-8");
            } else {
                bytes = fileName.getBytes();
            }
            fileName = new String(bytes, CharEncoding.ISO_8859_1); // 各浏览器基本都支持ISO编码
            ErrorCode eCode = HttpClient.getInstance().httpDownloadFile(fileId, response, direct, fileName,
                    fileExtName);
            if (eCode != ErrorCode.OK) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            log.error("download file error! fileId:" + fileId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
        }
    }

    /**
     * 删除指定文件
     *
     * @param fileId fastdfs返回的fileId
     */
    @RequestMapping(value = "/v1/del")
    public void deleteFile(@RequestParam("fileId") String fileId) {
        try {
            FileInfoEntity fileInfo = new FileInfoEntity();
            fileInfo.setFileId(fileId);
            fileInfo.setUpdateDate(new Date());
            fileInfo.setStatus(FileInfoEntity.FILE_STATUS_DELETED);
            fileInfoService.updateFileInfoByFileId(fileInfo);
        } catch (Exception e) {
            log.error("delete file data error!", e);
        }
    }

}