package com.example.camunda_multi_task;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@EnableProcessApplication
@Slf4j
public class CamundaMultiTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamundaMultiTaskApplication.class, args);
    }

}

interface Constants {
    AtomicInteger idGenerator = new AtomicInteger(1000);
    String RIVENDELL_SERVICE = "rivendell-service";
    String HAZHIR = "hazhir";
    String RIVENDELL_UI = "rivendell-ui";
    String BEHNAM = "behnam";
    String ARASH = "arash";
    String RAMEN = "ramen";
    String EOS = "eos";
    String MARIADB = "mariadb";
    String ISENGARD = "isengard";
    Map<String, String> applications = Map.of(RIVENDELL_SERVICE, HAZHIR
            , RIVENDELL_UI, BEHNAM
            , RAMEN, ARASH
            , EOS, ARASH
            , MARIADB, HAZHIR
            , ISENGARD, HAZHIR
    );
}

@Controller
class MainController {


    @Autowired
    RuntimeService runtimeService;

    @GetMapping("/run")
    public ResponseEntity<String> runProcess() {

        RoleRequest request = RoleRequest.builder()
                .items(List.of(
                        RoleRequestItem.builder().application(Constants.RIVENDELL_SERVICE).role("admin").manager("javier").user(Constants.HAZHIR).build(),
                        RoleRequestItem.builder().application(Constants.RIVENDELL_SERVICE).role("support").manager("cheryl").user(Constants.ARASH).build(),
                        RoleRequestItem.builder().application(Constants.RIVENDELL_SERVICE).role("support").manager(Constants.HAZHIR).user(Constants.BEHNAM).build(),
                        RoleRequestItem.builder().application(Constants.RIVENDELL_SERVICE).role("admin").manager(Constants.HAZHIR).user(Constants.BEHNAM).build(),
                        RoleRequestItem.builder().application(Constants.RIVENDELL_SERVICE).role("support").manager(Constants.HAZHIR).user("matthew").build()
                ))
                .build();

        ProcessInstance result = runtimeService.startProcessInstanceByKey("ApplicationRoleRequest"
                , Map.of("request", Variables.objectValue(request).create()));
//        runtimeService.setVariable(result.getProcessInstanceId(), "request", request);

        return ResponseEntity.ok(result.getProcessInstanceId());
    }
}

@Data
@Builder
class RoleRequest implements Serializable {
    List<RoleRequestItem> items;
}

@Data
@Builder
class RoleRequestItem implements Serializable {
    @Builder.Default
    Integer id = Constants.idGenerator.incrementAndGet();
    @EqualsExclude
    String role;
    @EqualsExclude
    String application;
    @EqualsExclude
    String user;
    @EqualsExclude
    String manager;
    @EqualsExclude
    @Builder.Default
    Boolean managerApproval = null;
    @EqualsExclude
    @Builder.Default
    Boolean applicationApproval = null;
    @EqualsExclude
    @Builder.Default
    Boolean roleApproval = null;
}

@Component
@Slf4j
class RequestService {

    public static final String PROCESSED_ORDER_ITEMS = "processedOrderItems";
    public static final String PROCESSED_ORDER = "processedOrder";
    @Autowired
    RuntimeService runtimeService;

    public void validation(DelegateExecution execution) {
        log.info("validate(), variables: {}", execution.getVariables().size());
    }

    @Transactional
    public void assessment(DelegateExecution execution) {
        log.info("validate executed, variables: {}", execution.getVariables().size());
    }

    public void managerApproved(DelegateExecution execution, RoleRequestItem requestItem) {
        RoleRequestItem roleRequestItem = getRequestItem(execution, requestItem);
        roleRequestItem.setManagerApproval(true);
        execution.setVariable("request", getRequest(execution));
    }

    private RoleRequestItem getRequestItem(DelegateExecution execution, RoleRequestItem requestItem) {
        RoleRequest roleRequest = getRequest(execution);
        log.info("getting request item from {}, \n request item id: {}, \n request {}", execution.getProcessInstanceId(), requestItem.getId(), roleRequest);
        return roleRequest.getItems().stream().filter(i -> i.getId().equals(requestItem.getId())).findAny().get();
    }

    private RoleRequest getRequest(DelegateExecution execution) {
        return (RoleRequest) runtimeService.getVariable(execution.getProcessInstanceId(), "request");
    }

    public void managerRejected(DelegateExecution execution, RoleRequestItem requestItem) {
        RoleRequestItem roleRequestItem = getRequestItem(execution, requestItem);
        roleRequestItem.setManagerApproval(false);
        execution.setVariable("request", getRequest(execution));
    }

    public boolean isManagerConsentReceived(DelegateExecution execution, RoleRequestItem requestItem) {
        log.info("Checking..., {}", requestItem);
        RoleRequestItem roleRequestItem = getRequestItem(execution, requestItem);
        return roleRequestItem.getManagerApproval() != null;
    }

    public boolean isManagerApproved(DelegateExecution execution, RoleRequestItem requestItem) {
        log.info("Checking..., {}", requestItem);
        RoleRequestItem roleRequestItem = getRequestItem(execution, requestItem);
        return Boolean.TRUE.equals(roleRequestItem.getManagerApproval());
    }

    public Map<String, List<RoleRequestItem>> determineManagers(DelegateExecution execution) {
        RoleRequest roleRequest = getRequest(execution);
        Map<String, List<RoleRequestItem>> managers = new HashMap<>();
        for (RoleRequestItem item : roleRequest.getItems()) {
            managers.computeIfAbsent(item.getManager(), (k) -> new ArrayList<>()).add(item);
        }

        log.info("managers computed: {}", managers);
        return managers;
//        execution.setVariable("managers",
//                Variables.objectValue(managers).create());
    }

    public void hello() {
        System.out.println("Hello!");
    }

//    public Map<String, List<RoleRequestItem>> determineApplications(DelegateExecution execution) {
//        RoleRequest roleRequest = (RoleRequest) execution.getVariable("request");
//        Map<String, List<RoleRequestItem>> applications = new HashMap<>();
//        for (RoleRequestItem item : roleRequest.getItems()) {
//            applications.computeIfAbsent(item.getApplication(), (k) -> new ArrayList<>()).add(item);
//        }
//
//        log.info("applications computed: {}", applications);
//        return applications;
////        execution.setVariable("applications",
////                Variables.objectValue(applications).create());
//    }

//    public Boolean canProceedWithApplicatinApproval(DelegateExecution execution, String application) {
//        RoleRequest roleRequest = (RoleRequest) execution.getVariable("request");
//        for (RoleRequestItem item : roleRequest.getItems()) {
//            if (item.application.equals(application) && null != item.applicationApproval) {
//                return item.applicationApproval;
//            }
//        }
//    }

//    public void processItems(DelegateExecution execution) {
//        OrderItem orderItem = (OrderItem) execution.getVariable("item");
//        log.info("processing order item: {}", orderItem);
//
//        String storeNumber = "1";
//        if (Integer.parseInt(orderItem.getValue()) % 2 == 0) {
//            storeNumber = "2";
//        }
//
//        ProcessedOrder po = (ProcessedOrder) runtimeService.getVariable(execution.getProcessInstanceId(), PROCESSED_ORDER);
//        if (po == null) {
//            po = ProcessedOrder.builder().processedOrderItems(new ArrayList<>()).build();
//            runtimeService.setVariable(execution.getProcessInstanceId(), PROCESSED_ORDER, Variables.objectValue(po, true).create());
//        }
//
//        po.getProcessedOrderItems().add(ProcessedOrderItem.builder()
//                .store(storeNumber)
//                .value(orderItem.value)
//                .type(orderItem.type).build());
//
//
//        runtimeService.createMessageCorrelationAsync("OrderItemProcessed")
//                .setVariable("pItem", po)
//                .processInstanceIds(List.of(execution.getProcessInstanceId()))
//                .correlateAllAsync();
//
////        runtimeService.messageEventReceived("OrderItemProcessed", execution.getProcessInstanceId());
//
//        log.info("message sent");
//    }
//
//    public void messageReceived(DelegateExecution execution) {
//        log.info("signal received");
//    }
//
//    public void orderItemApprovalReceived(DelegateExecution execution) {
//        log.info("order item approval received");
//
//        runtimeService.createMessageCorrelationAsync("OrderItemApprovalReceived")
//                .processInstanceIds(List.of(execution.getProcessInstanceId()))
//                .correlateAllAsync();
//    }
//
//    public boolean isOrderItemApproved(DelegateExecution execution, OrderItem item) {
//        log.info("evaluating order item: {}", item);
//        return true;
//    }

//    public Integer processItemsCount(DelegateExecution execution) {
//        Object orderObj = execution.getVariable("order");
//        log.info("order obj: {}", orderObj);
//        return ((Order) orderObj).items.size();
//    }
}
