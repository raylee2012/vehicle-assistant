// OpenCL stub library for Android arm64-v8a build-time linking.
// Device's /vendor/lib64/libOpenCL.so resolves these at runtime.
// All symbols are weak — at runtime the vendor library overrides them.

#define CL_USE_DEPRECATED_OPENCL_1_2_APIS
#define CL_TARGET_OPENCL_VERSION 300
#include "CL/cl.h"
#include "CL/cl_gl.h"
#include "CL/cl_ext.h"

// ─── Platform ───
__attribute__((weak)) cl_int clGetPlatformIDs(cl_uint n, cl_platform_id *p, cl_uint *np) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetPlatformInfo(cl_platform_id p, cl_platform_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }

// ─── Device ───
__attribute__((weak)) cl_int clGetDeviceIDs(cl_platform_id p, cl_device_type t, cl_uint n, cl_device_id *d, cl_uint *r) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetDeviceInfo(cl_device_id d, cl_device_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }

// ─── Context & Queue ───
__attribute__((weak)) cl_context clCreateContext(const cl_context_properties *p, cl_uint n, const cl_device_id *d, void (*cb)(const char *, const void *, size_t, void *), void *u, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_command_queue clCreateCommandQueue(cl_context c, cl_device_id d, cl_command_queue_properties p, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }

// ─── Buffer & Image ───
__attribute__((weak)) cl_mem clCreateBuffer(cl_context c, cl_mem_flags f, size_t s, void *h, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_mem clCreateBufferWithProperties(cl_context c, const cl_mem_properties *p, cl_mem_flags f, size_t s, void *h, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_mem clCreateSubBuffer(cl_mem b, cl_mem_flags f, cl_buffer_create_type t, const void *i, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_mem clCreateImage(cl_context c, cl_mem_flags f, const cl_image_format *fmt, const cl_image_desc *desc, void *h, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_int clReleaseMemObject(cl_mem m) { return CL_SUCCESS; }

// ─── Program & Kernel ───
__attribute__((weak)) cl_program clCreateProgramWithSource(cl_context c, cl_uint n, const char **s, const size_t *l, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_program clCreateProgramWithBinary(cl_context c, cl_uint n, const cl_device_id *d, const size_t *l, const unsigned char **b, cl_int *s, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_int clBuildProgram(cl_program p, cl_uint n, const cl_device_id *d, const char *o, void (*cb)(cl_program, void *), void *u) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetProgramBuildInfo(cl_program p, cl_device_id d, cl_program_build_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetProgramInfo(cl_program p, cl_program_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clReleaseProgram(cl_program p) { return CL_SUCCESS; }
__attribute__((weak)) cl_kernel clCreateKernel(cl_program p, const char *n, cl_int *e) { if(e)*e=CL_SUCCESS; return NULL; }
__attribute__((weak)) cl_int clSetKernelArg(cl_kernel k, cl_uint i, size_t s, const void *v) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetKernelWorkGroupInfo(cl_kernel k, cl_device_id d, cl_kernel_work_group_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetKernelInfo(cl_kernel k, cl_kernel_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clGetKernelSubGroupInfo(cl_kernel k, cl_device_id d, cl_kernel_sub_group_info i, size_t is, const void *iv, size_t os, void *ov, size_t *r) { return CL_SUCCESS; }

// ─── Command Queue ───
__attribute__((weak)) cl_int clEnqueueNDRangeKernel(cl_command_queue q, cl_kernel k, cl_uint wd, const size_t *go, const size_t *gs, const size_t *ls, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueWriteBuffer(cl_command_queue q, cl_mem b, cl_bool bl, size_t o, size_t s, const void *p, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueReadBuffer(cl_command_queue q, cl_mem b, cl_bool bl, size_t o, size_t s, void *p, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueCopyBuffer(cl_command_queue q, cl_mem sb, cl_mem db, size_t so, size_t d, size_t s, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueFillBuffer(cl_command_queue q, cl_mem b, const void *p, size_t ps, size_t o, size_t s, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueBarrierWithWaitList(cl_command_queue q, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clEnqueueMarkerWithWaitList(cl_command_queue q, cl_uint ne, const cl_event *ew, cl_event *ev) { return CL_SUCCESS; }

// ─── Synchronization ───
__attribute__((weak)) cl_int clFinish(cl_command_queue q) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clFlush(cl_command_queue q) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clWaitForEvents(cl_uint n, const cl_event *e) { return CL_SUCCESS; }
__attribute__((weak)) cl_int clReleaseEvent(cl_event e) { return CL_SUCCESS; }

// ─── Profiling ───
__attribute__((weak)) cl_int clGetEventProfilingInfo(cl_event e, cl_profiling_info i, size_t s, void *v, size_t *r) { return CL_SUCCESS; }
