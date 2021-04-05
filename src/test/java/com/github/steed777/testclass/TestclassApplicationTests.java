package com.github.steed777.testclass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(SpringRunner.class)
public class FileServiceTest {
	@TestConfiguration
	static class DbFileServiceConfigurationContext {
		@Bean
		public DbFileService dbFileService() {
			return new DbFileServiceImpl();
		}
	}
	@TestConfiguration
	static class FileServiceTestConfiguration {
		@Bean
		public FileServiceImpl fileService() {
			return new FileServiceImpl();
		}
	}

	@MockBean
	FileRepository fileRepository;
	@MockBean
	FileFilterRepository fileFilterRepository;
	@MockBean
	OwnerRepository ownerRepository;
	@MockBean
	FolderDataRepository folderDataRepository;
	@MockBean
	ObjectMapper objectMapper;
	@MockBean
	MinioService minioService;

	@Autowired
	DbFileService dbFileService;


	@Value("${minio.bucket.name}")
	String bucketName;

	@Autowired
	FileService fileService;

	@Test
	public void fileServiceCreate_shouldCreateRecord() {
		FileObj mockFileObj = new FileObj();
		mockFileObj.setId(new UUID(1, 1));
		Mockito.when(dbFileService.create(Mockito.any())).thenReturn(mockFileObj);
		Mockito.when(objectMapper.fileToFileObjDTO(mockFileObj)).thenReturn(new FileObjDTO());

		String testFileData = "test file data";
		FileObjDTO createFileRequest = new FileObjDTO();
		createFileRequest.setFileData(testFileData);
		fileService.create(createFileRequest);

		Mockito.verify(minioService).putObjectIntoTheBucket(
				Mockito.eq(bucketName),
				Mockito.any(InputStream.class),
				Mockito.eq(mockFileObj.getId().toString())
		);
	}

	@Test
	public void fileServiceFilter_shouldReturnFileResponse() {
		FileObj mockFile = new FileObj();
		mockFile.setId(new UUID(1, 1));
		Page<FileObj> mockFileFilterResult = new PageImpl<>(Arrays.asList(mockFile));

		Mockito.when(fileFilterRepository.findByFilter(
				Mockito.anyBoolean(),
				Mockito.anyBoolean(),
				Mockito.anyBoolean(),
				Mockito.anyString(),
				Mockito.anyBoolean(),
				Mockito.any(UUID.class),
				Mockito.any(LocalDateTime.class),
				Mockito.any(LocalDateTime.class),
				Mockito.any(Pageable.class)
		)).thenReturn(mockFileFilterResult);

		FileFilterDto filterRequest = new FileFilterDto();
		filterRequest.setPaginating(new Paginating(10, 256));

		FileResponse result = fileService.filter(filterRequest);

		Mockito.verify(objectMapper).fileToFileObjDTO(Mockito.eq(mockFile));

		Assert.assertEquals("", result.getMessage());
		Assert.assertTrue(result.getSuccess());
		Assert.assertEquals(1, result.getFileObjPages().size());
	}

	@Test
	public void fileServiceUpdateWithoutFileData_shouldUpdateFileNameAndExtFields() {
		FileObj mockFileObj = new FileObj();
		mockFileObj.setId(new UUID(1, 1));
		mockFileObj.setFileName("old");
		mockFileObj.setExt("txt");

		FileObjDTO updateRequest = new FileObjDTO();
		updateRequest.setExt("py");
		updateRequest.setFileName("new");

		Mockito
				.when(fileRepository.getOne(Mockito.any(UUID.class)))
				.thenReturn(mockFileObj);
		Mockito
				.when(objectMapper.fileToFileObjDTO(Mockito.eq(mockFileObj)))
				.thenReturn(updateRequest);

		FileResponse result = fileService
				.update(new UUID(1, 1), updateRequest);

		FileObj expectedFileObj = new FileObj();
		expectedFileObj.setFileName("new");
		expectedFileObj.setExt("py");
		Mockito.verify(fileRepository).save(Mockito.eq(mockFileObj));
		Mockito.verify(objectMapper).fileToFileObjDTO(mockFileObj);

		Assert.assertEquals("", result.getMessage());
		Assert.assertTrue(result.getSuccess());
		Assert.assertEquals(1, result.getFileObjPages().size());
		Assert.assertEquals("new", result.getFileObjPages().get(0).getFileName());
		Assert.assertEquals("py", result.getFileObjPages().get(0).getExt());
	}

	@Test
	public void fileServiceUpdateWithFileData_shouldUpdateFieldsAndCallMinioService() {
		FileObj mockFileObj = new FileObj();
		mockFileObj.setId(new UUID(1, 1));
		mockFileObj.setFileName("old");
		mockFileObj.setExt("txt");

		FileObjDTO updateRequest = new FileObjDTO();
		updateRequest.setExt("py");
		updateRequest.setFileName("new");
		updateRequest.setFileData("hello world!");

		Mockito
				.when(fileRepository.getOne(Mockito.any(UUID.class)))
				.thenReturn(mockFileObj);
		Mockito
				.when(objectMapper.fileToFileObjDTO(Mockito.eq(mockFileObj)))
				.thenReturn(updateRequest);

		FileResponse result = fileService
				.update(new UUID(1, 1), updateRequest);

		Mockito.verify(fileRepository).save(Mockito.eq(mockFileObj));
		Mockito.verify(minioService).putObjectIntoTheBucket(
				Mockito.eq(bucketName),
				Mockito.any(InputStream.class),
				Mockito.eq(new UUID(1, 1).toString())
		);
		Mockito.verify(objectMapper).fileToFileObjDTO(mockFileObj);

		Assert.assertEquals("", result.getMessage());
		Assert.assertTrue(result.getSuccess());
		Assert.assertEquals(1, result.getFileObjPages().size());
		Assert.assertEquals("new", result.getFileObjPages().get(0).getFileName());
		Assert.assertEquals("py", result.getFileObjPages().get(0).getExt());
		Assert.assertNull(result.getFileObjPages().get(0).getFileData());
	}

	@Test
	public void fileServiceDeleteExistingFile_shouldSetDeletedFlagAndUpdateDateTime() {
		UUID mockUUID = new UUID(1, 1);
		LocalDateTime now = LocalDateTime.now();
		FileObj mockFileObj = new FileObj();
		mockFileObj.setId(mockUUID);
		mockFileObj.setDeleted(false);
		mockFileObj.setUpdateDateTime(now);

		Mockito
				.when(fileRepository.findById(mockUUID))
				.thenReturn(java.util.Optional.of(mockFileObj));

		FileResponse result = fileService.setDeletedStatusForFileByUuid(mockUUID);

		Mockito.verify(fileRepository).save(Mockito.refEq(mockFileObj, "isDeleted", "updatedAt"));

		Assert.assertEquals("", result.getMessage());
		Assert.assertTrue(result.getSuccess());
		Assert.assertNull(result.getFileObjPages());
	}

	@Test
	public void fileServiceDeleteDeletedFile_shouldNotSaveNewFileObj() {
		UUID mockUUID = new UUID(1, 1);
		LocalDateTime now = LocalDateTime.now();
		FileObj mockFileObj = new FileObj();
		mockFileObj.setId(mockUUID);
		mockFileObj.setDeleted(true);
		mockFileObj.setUpdateDateTime(now);

		Mockito
				.when(fileRepository.findById(mockUUID))
				.thenReturn(java.util.Optional.of(mockFileObj));

		FileResponse result = fileService.setDeletedStatusForFileByUuid(mockUUID);

		Mockito.verify(fileRepository, Mockito.never()).save(Mockito.any());

		Assert.assertEquals("Файл уже помечен на удаление", result.getMessage());
		Assert.assertTrue(result.getSuccess());
		Assert.assertNull(result.getFileObjPages());
	}
}
