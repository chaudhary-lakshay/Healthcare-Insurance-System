package com.lakshay.healthcare.admin.controller

import com.lakshay.healthcare.admin.dto.WorkerDataResponse
import com.lakshay.healthcare.user.dto.RegisterRequest
import com.lakshay.healthcare.user.service.WorkerMgmtService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/worker-api")
class AdminWorkerController(
    private val workerService: WorkerMgmtService
) {

    @PostMapping("/register")
    fun registerWorker(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val result = workerService.registerWorker(request)
        return ResponseEntity.ok(
            mapOf(
                "workerId" to result.id,
                "message" to "Worker registered successfully by admin"
            )
        )
    }

    @GetMapping("/all")
    fun listWorkers(): ResponseEntity<List<WorkerDataResponse>> {
        val workers = workerService.listWorkers()
        return ResponseEntity.ok(workers)
    }

    @GetMapping("/find/{id}")
    fun showWorkerById(@PathVariable id: Long): ResponseEntity<WorkerDataResponse> {
        val worker = workerService.showWorkerByWorkerId(id)
        return ResponseEntity.ok(worker)
    }

    @PutMapping("/update")
    fun updateWorker(@RequestBody request: WorkerDataResponse): ResponseEntity<Map<String, String>> {
        val message = workerService.updateWorker(request)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @DeleteMapping("/delete/{id}")
    fun deleteWorker(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val message = workerService.deleteWorkerById(id)
        return ResponseEntity.ok(mapOf("message" to message))
    }

    @PatchMapping("/status/{id}/{status}")
    fun changeWorkerStatus(
        @PathVariable id: Long,
        @PathVariable status: String
    ): ResponseEntity<Map<String, String>> {
        val message = workerService.changeWorkerStatus(id, status)
        return ResponseEntity.ok(mapOf("message" to message))
    }
}
