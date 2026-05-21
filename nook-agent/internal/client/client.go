// Package client 封装 backend HTTP 调用; 所有 push / pull 都走这里.
package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

const tokenHeader = "X-Agent-Token"

// Client 是 backend HTTP 客户端; 短连接, 每次新 Request.
type Client struct {
	apiURL string
	token  string
	http   *http.Client
}

// New 构造一个 client; baseURL 必须不带尾斜杠.
func New(apiURL, token string, timeout time.Duration) *Client {
	return &Client{
		apiURL: strings.TrimRight(apiURL, "/"),
		token:  token,
		http:   &http.Client{Timeout: timeout},
	}
}

// CommonResult 跟后端 Result<T> 对齐.
type CommonResult struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// Post 通用 JSON POST; body 为 nil 表示空 body. 拿到 200 且 code==0 才返 nil.
func (c *Client) Post(path string, body any, respData any) error {
	return c.do(http.MethodPost, path, body, respData)
}

// Get 通用 GET; respData 反序列化 Result.data.
func (c *Client) Get(path string, respData any) error {
	return c.do(http.MethodGet, path, nil, respData)
}

func (c *Client) do(method, path string, body any, respData any) error {
	var reqBody io.Reader
	if body != nil {
		buf, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("序列化 body 失败: %w", err)
		}
		reqBody = bytes.NewReader(buf)
	}
	req, err := http.NewRequest(method, c.apiURL+path, reqBody)
	if err != nil {
		return err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set(tokenHeader, c.token)
	req.Header.Set("User-Agent", "nook-agent")

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("HTTP 调用 %s %s 失败: %w", method, path, err)
	}
	defer resp.Body.Close()

	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("读响应体失败: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBytes))
	}
	var result CommonResult
	if err := json.Unmarshal(respBytes, &result); err != nil {
		return fmt.Errorf("解析 Result 失败: %w (body=%s)", err, string(respBytes))
	}
	if result.Code != 0 {
		return fmt.Errorf("业务错误 code=%d msg=%s", result.Code, result.Message)
	}
	if respData != nil && len(result.Data) > 0 {
		if err := json.Unmarshal(result.Data, respData); err != nil {
			return fmt.Errorf("解析 data 失败: %w", err)
		}
	}
	return nil
}
